/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.nevercode.triagemagic.view

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.awt.Label

class TriagemagicViewFactory : ToolWindowFactory, DumbAware {

  override fun init(toolWindow: ToolWindow) {
    // Whether Triagemagic tool window should be visible or not.
    // We can use this to disable Triagemagic for invalid projects.
    toolWindow.setAvailable(true, null)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    DumbService.getInstance(project).runWhenSmart {
      (ServiceManager.getService(project, TriagemagicView::class.java)).initToolWindow(project, toolWindow)
    }
  }
}