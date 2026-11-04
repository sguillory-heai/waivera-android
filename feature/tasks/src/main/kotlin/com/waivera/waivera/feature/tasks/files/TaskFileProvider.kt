package com.waivera.waivera.feature.tasks.files

import androidx.core.content.FileProvider

/**
 * Its own class so the merged manifest keeps both this provider and chat's; two entries naming
 * `androidx.core.content.FileProvider` collide when the app merges its modules.
 */
class TaskFileProvider : FileProvider()
