package com.waivera.waivera.feature.tasks.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ApprovalOutcome
import com.waivera.waivera.core.data.model.ApprovalPlan
import com.waivera.waivera.core.data.model.ApprovalSend
import com.waivera.waivera.core.data.model.LateContext
import com.waivera.waivera.core.data.model.LateEdit
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.PickPurpose
import com.waivera.waivera.core.data.repository.TaskReviewRepository
import com.waivera.waivera.core.data.repository.TaskUploadRepository
import com.waivera.waivera.core.data.uploads.UploadRefused
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.feature.tasks.Loadable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TaskReviewEffect {
    /** The task moved on; [completed] when this approval finished it. */
    data class Done(val action: ReviewAction, val completed: Boolean) : TaskReviewEffect

    data object SentBack : TaskReviewEffect

    /** Someone else acted first, or the workflow changed: the task was refreshed. */
    data object Changed : TaskReviewEffect

    /** A step failed before any sheet was open. */
    data object Failed : TaskReviewEffect

    /** A late reason is needed and only the assignee or their manager can send it. */
    data object LateNeedsAssignee : TaskReviewEffect

    data object CantApproveAll : TaskReviewEffect

    /** The task needs reloading (always sent after the chain changes it). */
    data object Reload : TaskReviewEffect

    data class OpenFile(val attachmentId: Int) : TaskReviewEffect

    data class OpenTask(val taskId: Int) : TaskReviewEffect
}

/**
 * Submit, Approve and Reject with every step sprout_web's `TaskPage.HandleApprove` runs, in the
 * same order: the late reason (client tasks past due) and its review, the requirements check,
 * "approve all" confirmation, unfinished forms, the Drive section of a reopened task, the employee
 * closure choice, the stage's own requirements, then the send and the related-task answers.
 * Each step is one sheet; finishing it moves the chain on, closing it stops the chain.
 */
@HiltViewModel(assistedFactory = TaskReviewViewModel.Factory::class)
@Suppress("TooManyFunctions") // One handler per sheet action of the chain.
class TaskReviewViewModel @AssistedInject constructor(
    @Assisted private val taskId: Int,
    private val review: TaskReviewRepository,
    private val uploads: TaskUploadRepository,
    calendar: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = state.asStateFlow()
    private val effectChannel = Channel<TaskReviewEffect>(Channel.BUFFERED)
    val effects: Flow<TaskReviewEffect> = effectChannel.receiveAsFlow()
    private val zone = calendar.zone.stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)

    /** Where the running chain stands; reset when it ends. */
    private var chain: Chain? = null
    private var preparing: Job? = null

    private data class Chain(
        val action: ReviewAction,
        val task: TaskDetail,
        val lateChecked: Boolean = false,
        val plan: ApprovalPlan? = null,
        val confirmedAll: Boolean = false,
        val draftsChecked: Boolean = false,
        val discardDrafts: Boolean = false,
        val useOriginal: Boolean? = null,
        val closurePrepared: Boolean = false,
        val answers: Map<Int, Map<String, String>>? = null,
        val acknowledged: Boolean = false,
        /** The server changed what the stage needs; the refetched questions say so. */
        val requirementsChanged: Boolean = false,
        val idempotencyKey: String = UUID.randomUUID().toString(),
    )

    private val today: LocalDate get() = clock.instant().atZone(zone.value).toLocalDate()

    fun start(action: ReviewAction, task: TaskDetail) {
        if (state.value.running != null || state.value.rejecting) return
        chain = Chain(action, task)
        state.update { it.copy(running = action, step = null) }
        advance()
    }

    /** Closing any sheet stops the chain where it is; nothing was sent yet unless it says so. */
    fun dismiss() {
        val current = state.value
        if (current.stepSaving) return
        (current.step as? ReviewStep.LateReason)?.form?.evidence?.let { file ->
            viewModelScope.launch { uploads.discard(file) }
        }
        preparing?.cancel()
        chain = null
        state.value = ReviewUiState()
    }

    // ----------------------------------------------------------------- the chain

    private fun advance() {
        val current = chain ?: return
        viewModelScope.launch {
            when {
                needsLateCheck(current) -> checkLate(current)
                current.plan == null -> loadPlan(current)
                else -> afterPlan(current, current.plan)
            }
        }
    }

    /** sprout_web checks the late reason for client tasks past their (effective) due day only. */
    private fun needsLateCheck(chain: Chain): Boolean {
        val due = chain.task.due ?: return false
        // The server compares UTC days; checking when either day is past lets the server's answer decide.
        val past = today.isAfter(due) || clock.instant().atZone(ZoneOffset.UTC).toLocalDate().isAfter(due)
        return !chain.lateChecked && chain.task.isClientTask && !chain.task.isFinished && past
    }

    private suspend fun checkLate(current: Chain) {
        val context = review.lateContext(taskId).getOrElse { return fail() }
        chain = current.copy(lateChecked = true)
        when {
            !context.late && !context.satisfied -> advance()
            !context.satisfied && !context.canSubmit -> stop(TaskReviewEffect.LateNeedsAssignee)
            !context.satisfied -> openLateForm(context, current.task.due)
            context.canReview && !context.reviewedByMe -> show(ReviewStep.LateReview(LateCheck(context)))
            else -> advance()
        }
    }

    private suspend fun loadPlan(current: Chain) {
        val mode = current.action.mode
        val plan = review.plan(taskId, mode).getOrElse { return fail() }
        if (plan.mode != mode || (mode == ApprovalMode.AllRemaining && !plan.canApproveAll)) {
            stop(TaskReviewEffect.CantApproveAll, reload = true)
        } else {
            val next = current.copy(plan = plan)
            chain = next
            afterPlan(next, plan)
        }
    }

    private suspend fun afterPlan(current: Chain, plan: ApprovalPlan) {
        val previousFiling = plan.previousFiling
        when {
            plan.mode == ApprovalMode.AllRemaining && !current.confirmedAll -> show(
                ReviewStep.ConfirmAll(plan.remainingStages, submit = current.action == ReviewAction.SubmitAll),
            )
            plan.willComplete && !current.draftsChecked -> checkDrafts(current)
            plan.willComplete && previousFiling != null && current.useOriginal == null ->
                show(ReviewStep.DriveSection(previousFiling))
            !current.task.isClientTask && plan.willComplete && !current.closurePrepared -> prepareClosure(current)
            plan.requirements.isNotEmpty() && current.answers == null -> show(
                ReviewStep.Requirements(
                    RequirementsForm(
                        plan.requirements,
                        RequirementChecks.initial(plan.requirements),
                        changed = current.requirementsChanged,
                    ),
                ),
            )
            else -> send(current, plan)
        }
    }

    private suspend fun checkDrafts(current: Chain) {
        val drafts = review.drafts(taskId).getOrElse { return fail() }
        chain = current.copy(draftsChecked = true)
        if (drafts.isEmpty()) advance() else show(ReviewStep.Drafts(drafts.map { it.name }.filter(String::isNotBlank)))
    }

    private suspend fun prepareClosure(current: Chain) {
        val options = review.employeeClosure(taskId).getOrElse { return fail() }
        when {
            options.asks -> show(ReviewStep.Closure(ClosureForm(options)))
            // A plain close replaces any merge saved earlier, as the web does.
            review.chooseClosure(taskId, merge = null, nextExpiration = null).isFailure -> fail()
            else -> {
                chain = current.copy(closurePrepared = true)
                advance()
            }
        }
    }

    private suspend fun send(current: Chain, plan: ApprovalPlan) {
        setSaving(true)
        val outcome = review.approve(
            taskId,
            ApprovalSend(
                plan = plan,
                idempotencyKey = current.idempotencyKey,
                answers = current.answers.orEmpty(),
                acknowledgeWarnings = current.acknowledged,
                discardDrafts = current.discardDrafts,
                useOriginalSection = current.useOriginal == true,
            ),
        ).getOrElse {
            setSaving(false)
            return failInSheet()
        }
        when (outcome) {
            is ApprovalOutcome.Approved -> stop(TaskReviewEffect.Done(current.action, outcome.completed), reload = true)
            is ApprovalOutcome.Blocked -> show(ReviewStep.Related(blocked = true, related = outcome.related))
            is ApprovalOutcome.NeedsAcknowledgement -> show(
                ReviewStep.Related(blocked = false, related = outcome.related),
            )
            is ApprovalOutcome.RequirementsChanged -> requirementsChanged(current)
            ApprovalOutcome.Stale -> stop(TaskReviewEffect.Changed, reload = true)
        }
    }

    /**
     * The server's workflow version includes the requirements version, so the plan is fetched
     * again for both before asking; resending with the old one would be refused the same way.
     */
    private fun requirementsChanged(current: Chain) {
        chain = current.copy(
            plan = null,
            answers = null,
            requirementsChanged = true,
            idempotencyKey = UUID.randomUUID().toString(),
        )
        advance()
    }

    // ----------------------------------------------------------------- sheet answers

    fun confirmAll() = moveOn { it.copy(confirmedAll = true) }

    fun discardDrafts() = moveOn { it.copy(discardDrafts = true) }

    fun chooseDrive(original: Boolean) = updateStep<ReviewStep.DriveSection> { it.copy(useOriginal = original) }

    fun confirmDrive() {
        val step = state.value.step as? ReviewStep.DriveSection ?: return
        moveOn { it.copy(useOriginal = step.useOriginal) }
    }

    fun completeAnyway() {
        val step = state.value.step as? ReviewStep.Related ?: return
        if (step.blocked || step.isSaving) return
        val current = chain ?: return
        chain = current.copy(acknowledged = true)
        state.update { it.copy(step = step.copy(isSaving = true)) }
        advance()
    }

    fun openRelated(taskId: Int) {
        viewModelScope.launch { effectChannel.send(TaskReviewEffect.OpenTask(taskId)) }
        dismiss()
    }

    // Employee closure.

    fun setMerge(merge: Boolean) = updateClosure { it.copy(merge = merge, failed = false) }

    fun togglePdf(id: String) = updateClosure { form ->
        form.copy(picked = if (id in form.picked) form.picked - id else form.picked + id, failed = false)
    }

    fun movePdf(id: String, up: Boolean) = updateClosure { form ->
        val index = form.order.indexOf(id)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in form.order.indices) {
            form
        } else {
            form.copy(order = form.order.toMutableList().apply { add(target, removeAt(index)) })
        }
    }

    fun setExpiration(date: LocalDate) = updateClosure { it.copy(expiration = date, failed = false) }

    fun saveClosure() {
        val form = (state.value.step as? ReviewStep.Closure)?.form ?: return
        if (form.isSaving) return
        if (!form.isValid) return updateClosure { it.copy(showErrors = true) }
        updateClosure { it.copy(isSaving = true, failed = false) }
        viewModelScope.launch {
            val merge = form.order.filter { it in form.picked }.takeIf { form.merge }
            review.chooseClosure(taskId, merge, form.expiration.takeIf { form.options.needsExpiration })
                .onSuccess { moveOn { it.copy(closurePrepared = true) } }
                .onFailure { updateClosure { it.copy(isSaving = false, failed = true) } }
        }
    }

    // Stage requirements.

    fun setRequirement(actionId: Int, key: String, value: String) = updateRequirements { form ->
        val values = form.values + (actionId to (form.values[actionId].orEmpty() + (key to value)))
        form.copy(values = values, confirmWarnings = false, failed = false)
    }

    fun submitRequirements() {
        val form = (state.value.step as? ReviewStep.Requirements)?.form ?: return
        if (form.isSaving || !form.supported) return
        val planDates = chain?.task?.planDates
        val results = form.requirements.associate {
            it.actionId to RequirementChecks.check(it, form.values[it.actionId].orEmpty(), planDates, today)
        }
        val errors = results.mapValues { it.value.errors }.filterValues { it.isNotEmpty() }
        val warnings = results.mapValues { it.value.warnings }.filterValues { it.isNotEmpty() }
        when {
            errors.isNotEmpty() -> updateRequirements { it.copy(errors = errors, warnings = warnings) }
            warnings.isNotEmpty() && !form.confirmWarnings ->
                updateRequirements { it.copy(errors = emptyMap(), warnings = warnings, confirmWarnings = true) }
            else -> {
                updateRequirements { it.copy(errors = emptyMap(), isSaving = true, failed = false) }
                val current = chain ?: return
                chain = current.copy(answers = form.values)
                advance()
            }
        }
    }

    // ----------------------------------------------------------------- the late reason

    private suspend fun openLateForm(context: LateContext, due: LocalDate?) {
        val returned = context.justification?.takeIf { it.returned }
        val delay = context.suggestedDelay.takeIf { returned == null }
        show(
            ReviewStep.LateReason(
                LateForm(
                    reasonId = returned?.reasonId ?: delay?.reasonId,
                    explanation = returned?.explanation ?: delay?.explanation.orEmpty(),
                    typed = (returned?.explanation ?: delay?.explanation).orEmpty().isNotEmpty(),
                    delayId = delay?.id,
                    delayEvidenceId = delay?.evidenceId,
                    existingEvidenceId = returned?.evidenceId,
                    returned = returned != null,
                    correction = context.correction,
                    due = due,
                ),
            ),
        )
        loadLateReasons()
    }

    fun loadLateReasons() {
        updateLate { it.copy(reasons = Loadable.Loading) }
        viewModelScope.launch {
            val result = review.lateReasons()
            updateLate { form -> form.copy(reasons = result.fold({ Loadable.Ready(it) }, { Loadable.Failed })) }
        }
    }

    /** web `applyPreset`: a reason's preset fills the explanation only until something is typed. */
    fun setLateReason(id: Long) = updateLate { form ->
        val reason = (form.reasons as? Loadable.Ready)?.value?.firstOrNull { it.id == id } ?: return@updateLate form
        form.copy(
            reasonId = id,
            explanation = if (form.typed) form.explanation else reason.preset.orEmpty(),
            // Another reason than the delay's no longer reuses the delay.
            delayId = form.delayId.takeIf { id == form.reasonId },
            failed = false,
        )
    }

    fun setLateExplanation(text: String) = updateLate { it.copy(explanation = text, typed = true, failed = false) }

    /** web "Use a different justification": forget the reported delay and start blank. */
    fun dropDelay() = updateLate { it.copy(delayId = null, reasonId = null, explanation = "", typed = false) }

    fun pickEvidence(uris: List<String>) {
        val uri = uris.firstOrNull() ?: return
        val form = (state.value.step as? ReviewStep.LateReason)?.form ?: return
        if (form.isSaving) return
        form.evidence?.let { old -> viewModelScope.launch { uploads.discard(old) } }
        updateLate { it.copy(evidence = null, evidenceChecking = true, evidenceProblem = null, failed = false) }
        preparing?.cancel()
        preparing = viewModelScope.launch {
            val result = uploads.prepare(uri, PickPurpose.Evidence)
            updateLate {
                it.copy(
                    evidence = result.getOrNull(),
                    evidenceChecking = false,
                    evidenceProblem = result.exceptionOrNull()?.let(::problemOf),
                )
            }
        }
    }

    fun removeEvidence() {
        val form = (state.value.step as? ReviewStep.LateReason)?.form ?: return
        if (form.isSaving) return
        form.evidence?.let { file -> viewModelScope.launch { uploads.discard(file) } }
        updateLate { it.copy(evidence = null, evidenceProblem = null) }
    }

    fun submitLate() {
        val form = (state.value.step as? ReviewStep.LateReason)?.form ?: return
        if (form.isSaving) return
        val reasonId = form.reasonId
        if (!form.isValid || reasonId == null) return updateLate { it.copy(showErrors = true) }
        updateLate { it.copy(isSaving = true, failed = false, evidenceProblem = null) }
        viewModelScope.launch {
            val evidenceId = uploadEvidence(form) ?: return@launch
            val explanation = form.explanation.trim()
            val result = form.editingId?.let { id ->
                review.agreeLate(taskId, id, note = null, edit = LateEdit(reasonId, explanation, evidenceId.value))
            } ?: review.justify(taskId, reasonId, explanation, evidenceId.value, form.delayId)
            result
                .onSuccess { lateSaved() }
                .onFailure { updateLate { it.copy(isSaving = false, failed = true) } }
        }
    }

    /** The picked file's attachment id, the kept one, or none; null stops the send. */
    private suspend fun uploadEvidence(form: LateForm): Evidence? {
        val file = form.evidence ?: return Evidence(form.keptEvidenceId)
        return uploads.sendEvidence(taskId, file).fold(
            onSuccess = { id ->
                // Up once: a retry after a failed justification reuses it instead of uploading again.
                // The reported delay stays linked; its evidence is now this upload.
                updateLate { it.copy(evidence = null, existingEvidenceId = id, delayEvidenceId = id) }
                Evidence(id)
            },
            onFailure = { error ->
                val problem = problemOf(error)
                updateLate { it.copy(isSaving = false, evidenceProblem = problem) }
                null
            },
        )
    }

    private data class Evidence(val value: Int?)

    /**
     * The late reason is saved, so its sheet closes: a later step that fails then reports its own
     * failure instead of offering to send the reason again.
     */
    private suspend fun lateSaved() {
        state.update { it.copy(step = null) }
        effectChannel.send(TaskReviewEffect.Reload)
        advance()
    }

    fun agreeLate() {
        val step = state.value.step as? ReviewStep.LateReview ?: return
        val check = step.check
        val justification = check.context.justification ?: return
        if (check.isSaving || check.noteTooLong) return
        updateLateCheck { it.copy(isSaving = true, failed = false) }
        viewModelScope.launch {
            review.agreeLate(taskId, justification.id, check.note, edit = null)
                .onSuccess { lateSaved() }
                .onFailure { updateLateCheck { it.copy(isSaving = false, failed = true) } }
        }
    }

    fun setLateNote(text: String) = updateLateCheck { it.copy(note = text, failed = false) }

    /** The reviewer corrects the reason themself, then agrees with the corrected one. */
    fun editLate() {
        val check = (state.value.step as? ReviewStep.LateReview)?.check ?: return
        val justification = check.context.justification ?: return
        show(
            ReviewStep.LateReason(
                LateForm(
                    reasonId = justification.reasonId,
                    explanation = justification.explanation,
                    typed = justification.explanation.isNotEmpty(),
                    existingEvidenceId = justification.evidenceId,
                    editingId = justification.id,
                    due = chain?.task?.due,
                ),
            ),
        )
        loadLateReasons()
    }

    fun openEvidence(attachmentId: Int) {
        viewModelScope.launch { effectChannel.send(TaskReviewEffect.OpenFile(attachmentId)) }
    }

    /** Return the late reason for correction: the task goes back to a chosen earlier step. */
    fun returnLate() {
        val check = (state.value.step as? ReviewStep.LateReview)?.check ?: return
        val justification = check.context.justification ?: return
        openSendBack(lateReasonId = justification.id)
    }

    // ----------------------------------------------------------------- send back

    fun startReject() {
        if (state.value.running != null || state.value.rejecting) return
        state.update { it.copy(rejecting = true) }
        openSendBack(lateReasonId = null)
    }

    private fun openSendBack(lateReasonId: Long?) {
        show(ReviewStep.SendBack(SendBackForm(lateReasonId = lateReasonId)))
        loadSendBackOptions()
    }

    fun loadSendBackOptions() {
        updateSendBack { it.copy(options = Loadable.Loading) }
        viewModelScope.launch {
            val result = review.sendBackOptions(taskId)
            updateSendBack { form ->
                val options = result.getOrNull()
                form.copy(
                    options = options?.let { Loadable.Ready(it) } ?: Loadable.Failed,
                    // One choice is picked for the person, as the web does.
                    approvalId = form.approvalId ?: options?.singleOrNull()?.approvalId,
                )
            }
        }
    }

    fun chooseSendBack(approvalId: Int) = updateSendBack { it.copy(approvalId = approvalId, failed = false) }

    fun setFeedback(text: String) = updateSendBack { it.copy(feedback = text, failed = false) }

    fun setMinutes(text: String) =
        updateSendBack { it.copy(minutes = text.filter(Char::isDigit).take(MINUTE_DIGITS), failed = false) }

    fun submitSendBack() {
        val form = (state.value.step as? ReviewStep.SendBack)?.form ?: return
        if (form.isSaving) return
        val option = form.option
        val minutes = form.minutesValue
        if (!form.isValid || option == null || minutes == null) return updateSendBack { it.copy(showErrors = true) }
        updateSendBack { it.copy(isSaving = true, failed = false) }
        viewModelScope.launch {
            review.sendBack(taskId, option, form.feedback.trim(), minutes, form.lateReasonId)
                .onSuccess { stop(TaskReviewEffect.SentBack, reload = true) }
                .onFailure { updateSendBack { it.copy(isSaving = false, failed = true) } }
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun moveOn(transform: (Chain) -> Chain) {
        val current = chain ?: return
        chain = transform(current)
        // A confirmation closes as it is answered, as the web's do; the button shows the progress.
        state.update { ui ->
            val step = ui.step
            val closes = step is ReviewStep.ConfirmAll || step is ReviewStep.Drafts || step is ReviewStep.DriveSection
            ui.copy(step = if (closes) null else step?.withSaving())
        }
        advance()
    }

    private fun show(step: ReviewStep) = state.update { it.copy(step = step) }

    private fun stop(effect: TaskReviewEffect, reload: Boolean = false) {
        chain = null
        state.value = ReviewUiState()
        viewModelScope.launch {
            effectChannel.send(effect)
            if (reload) effectChannel.send(TaskReviewEffect.Reload)
        }
    }

    /** A load failed: with a sheet open it says so there; otherwise the chain stops with a message. */
    private fun fail() {
        if (state.value.step == null) stop(TaskReviewEffect.Failed) else failInSheet()
    }

    private fun failInSheet() {
        when (state.value.step) {
            null -> stop(TaskReviewEffect.Failed)
            is ReviewStep.Requirements -> updateRequirements { it.copy(isSaving = false, failed = true) }
            is ReviewStep.Closure -> updateClosure { it.copy(isSaving = false, failed = true) }
            is ReviewStep.LateReason -> updateLate { it.copy(isSaving = false, failed = true) }
            is ReviewStep.LateReview -> updateLateCheck { it.copy(isSaving = false, failed = true) }
            // The confirmations have no room for an error, so they close and say it once.
            else -> stop(TaskReviewEffect.Failed)
        }
    }

    private fun setSaving(saving: Boolean) = state.update { it.copy(step = it.step?.withSaving(saving)) }

    private fun ReviewStep.withSaving(saving: Boolean = true): ReviewStep = when (this) {
        is ReviewStep.Requirements -> copy(form = form.copy(isSaving = saving))
        is ReviewStep.Closure -> copy(form = form.copy(isSaving = saving))
        is ReviewStep.LateReason -> copy(form = form.copy(isSaving = saving))
        is ReviewStep.LateReview -> copy(check = check.copy(isSaving = saving))
        is ReviewStep.Related -> copy(isSaving = saving)
        is ReviewStep.SendBack -> copy(form = form.copy(isSaving = saving))
        else -> this
    }

    private inline fun <reified T : ReviewStep> updateStep(crossinline transform: (T) -> ReviewStep) =
        state.update { current -> (current.step as? T)?.let { current.copy(step = transform(it)) } ?: current }

    private fun updateLate(transform: (LateForm) -> LateForm) =
        updateStep<ReviewStep.LateReason> { it.copy(form = transform(it.form)) }

    private fun updateLateCheck(transform: (LateCheck) -> LateCheck) =
        updateStep<ReviewStep.LateReview> { it.copy(check = transform(it.check)) }

    private fun updateClosure(transform: (ClosureForm) -> ClosureForm) =
        updateStep<ReviewStep.Closure> { it.copy(form = transform(it.form)) }

    private fun updateRequirements(transform: (RequirementsForm) -> RequirementsForm) =
        updateStep<ReviewStep.Requirements> { it.copy(form = transform(it.form)) }

    private fun updateSendBack(transform: (SendBackForm) -> SendBackForm) =
        updateStep<ReviewStep.SendBack> { it.copy(form = transform(it.form)) }

    private fun problemOf(error: Throwable): UploadProblem = (error as? UploadRefused)?.problem ?: UploadProblem.Failed

    @AssistedFactory
    interface Factory {
        fun create(taskId: Int): TaskReviewViewModel
    }

    private companion object {
        const val MINUTE_DIGITS = 4
    }
}
