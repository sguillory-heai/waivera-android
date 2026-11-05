package com.waivera.waivera.feature.tasks.review

/** The sheets' answers, each sent to the chain. */
internal fun reviewSheetActions(viewModel: TaskReviewViewModel, onOpenWeb: () -> Unit) = ReviewSheetActions(
    onDismiss = viewModel::dismiss,
    onConfirmAll = viewModel::confirmAll,
    onChooseScope = viewModel::chooseScope,
    onDiscardDrafts = viewModel::discardDrafts,
    onChooseDrive = viewModel::chooseDrive,
    onConfirmDrive = viewModel::confirmDrive,
    onCompleteAnyway = viewModel::completeAnyway,
    onOpenRelated = viewModel::openRelated,
    closure = ClosureActions(
        onMerge = viewModel::setMerge,
        onToggle = viewModel::togglePdf,
        onMove = viewModel::movePdf,
        onExpiration = viewModel::setExpiration,
        onSave = viewModel::saveClosure,
    ),
    requirements = RequirementActions(
        onValue = viewModel::setRequirement,
        onSubmit = viewModel::submitRequirements,
        onOpenWeb = {
            viewModel.dismiss()
            onOpenWeb()
        },
    ),
    late = LateActions(
        onRetryReasons = viewModel::loadLateReasons,
        onReason = viewModel::setLateReason,
        onExplanation = viewModel::setLateExplanation,
        onDropDelay = viewModel::dropDelay,
        onEvidencePicked = viewModel::pickEvidence,
        onRemoveEvidence = viewModel::removeEvidence,
        onSubmit = viewModel::submitLate,
        onAgree = viewModel::agreeLate,
        onEdit = viewModel::editLate,
        onReturn = viewModel::returnLate,
        onNote = viewModel::setLateNote,
        onOpenEvidence = viewModel::openEvidence,
    ),
    sendBack = SendBackActions(
        onRetry = viewModel::loadSendBackOptions,
        onChoose = viewModel::chooseSendBack,
        onFeedback = viewModel::setFeedback,
        onMinutes = viewModel::setMinutes,
        onSubmit = viewModel::submitSendBack,
    ),
)
