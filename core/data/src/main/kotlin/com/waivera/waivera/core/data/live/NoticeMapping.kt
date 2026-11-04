package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Notice
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.network.dto.EventNotificationResponse
import com.waivera.waivera.core.network.dto.NotificationResourceDto

/**
 * Turns the web's notification rows into [Notice]s. The web renders `{task}`, `{author}` and
 * `{activity}` from the resources as links; the phone fills them as plain text and drops any it
 * cannot fill, so a row never shows braces. Mirrors sprout_api `MobilePushNotificationService.InboxPush`.
 */
object NoticeMapping {
    private val Placeholder = Regex("\\{[A-Za-z]+}")
    private val Spaces = Regex("\\s{2,}")
    private val TaskPath = Regex("^/task/(\\d+)")
    private val EventQuery = Regex("[?&]eventId=([A-Za-z0-9_.:-]{1,128})")

    fun notice(dto: EventNotificationResponse): Notice? {
        if (dto.id.isBlank()) return null
        val resources = dto.notificationResources
        val taskId = resources.firstOrNull { it.resourceType.equals("task", ignoreCase = true) }
            ?.let { TaskPath.find(it.resourceId)?.groupValues?.get(1) }
        val eventId = resources.firstOrNull { it.resourceType.equals("meeting", ignoreCase = true) }
            ?.let { EventQuery.find(it.resourceId)?.groupValues?.get(1) }
            ?: dto.actionUrl?.takeIf { it.startsWith("/calendar") }?.let { EventQuery.find(it)?.groupValues?.get(1) }
        return Notice(
            id = dto.id,
            kind = kind(dto.type, resources),
            title = render(dto.title, resources),
            message = render(dto.message, resources),
            createdAt = dto.createdAt,
            read = dto.read,
            taskId = taskId,
            eventId = eventId,
            webPath = webPath(dto.actionUrl, resources),
        )
    }

    fun render(template: String?, resources: List<NotificationResourceDto>): String {
        if (template.isNullOrBlank()) return ""
        fun named(type: String) = resources.firstOrNull { it.resourceType.equals(type, ignoreCase = true) }
            ?.resourceName?.trim()?.takeIf(String::isNotEmpty)
        val tokens = mapOf("{task}" to named("task"), "{author}" to named("user"), "{activity}" to named("task_action"))
        return template.replace(Placeholder) { tokens[it.value.lowercase()].orEmpty() }.replace(Spaces, " ").trim()
    }

    /** Only same-site paths, never a full URL, so a row can only open the configured web app. */
    private fun webPath(actionUrl: String?, resources: List<NotificationResourceDto>): String? {
        val candidates = listOfNotNull(actionUrl) + resources
            .filter { it.resourceType.lowercase() in WebResources }
            .map { it.resourceId }
        return candidates.firstOrNull { it.startsWith("/") && !it.startsWith("//") }
    }

    private val WebResources = setOf("task", "task_action", "launchsurvey", "file", "meeting")

    /** The type names the kind; when it is unknown, the resources it links to do. */
    private fun kind(type: String?, resources: List<NotificationResourceDto>): NoticeKind =
        kindOfType(type.orEmpty().lowercase()) ?: kindOfResources(resources) ?: NoticeKind.Other

    private fun kindOfType(t: String): NoticeKind? = when {
        t == "task.assigned" || t == "participant_assignment" -> NoticeKind.TaskAssigned
        t == "task.approval_requested" || "approval" in t -> NoticeKind.Approval
        t.startsWith("task.") -> NoticeKind.TaskActivity
        "signature" in t || "esign" in t -> NoticeKind.Signature
        "survey" in t -> NoticeKind.Survey
        t == "meeting" -> NoticeKind.Meeting
        "time-off" in t || "timeoff" in t || "leave" in t -> NoticeKind.TimeOff
        else -> null
    }

    private fun kindOfResources(resources: List<NotificationResourceDto>): NoticeKind? {
        fun has(type: String) = resources.any { it.resourceType.equals(type, ignoreCase = true) }
        return when {
            has("launchsurvey") -> NoticeKind.Survey
            has("meeting") -> NoticeKind.Meeting
            has("task") -> NoticeKind.TaskActivity
            else -> null
        }
    }
}
