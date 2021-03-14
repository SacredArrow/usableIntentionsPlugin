import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.annotations.NotNull
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.progress.util.ReadTask
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlin.system.measureTimeMillis


class RunPluginAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        GlobalStorage.out_path = System.getenv()["OUT_PATH"]
        val project = e.getData(LangDataKeys.PROJECT)!!
        val editor = e.getData(LangDataKeys.EDITOR)!!
        println("Works")
//        val progress = ProgressWindow(true, project)
//        ProgressManager.getInstance().runProcess({createPopup(e)}, progress)
//        ProgressManager.getInstance().run(object: Task.Modal(project, "Title", true) {
//            override fun run(indicator: ProgressIndicator) {
//                createPopup(e)
//            }
//
//        })
//        ReadAction.nonBlocking { createPopup(e) }.submit(NonUrgentExecutor.getInstance())
        val time = measureTimeMillis {
            createPopup(e)
        }
        println(time)
    }

    fun createPopup(e : AnActionEvent) {
        val handler = CurrentPositionHandler(e)
        val actionGroup = ActionGroupBuilder().build(handler)
        val popup = JBPopupFactory.getInstance().createActionGroupPopup("Choose Intention", actionGroup, SimpleDataContext.getProjectContext(handler.project), true, null, -1)
        popup.showInBestPositionFor(handler.editor)
    }


}