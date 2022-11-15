package com.android.mdl.app.viewmodel

sealed interface EngagementStatus

object Engaged: EngagementStatus

object Completed: EngagementStatus