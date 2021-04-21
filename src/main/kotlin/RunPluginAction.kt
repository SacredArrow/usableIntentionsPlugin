import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor.Companion.getShortcutText
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import kotlinx.coroutines.*
import java.awt.event.ActionEvent
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.SwingConstants
import kotlin.concurrent.thread


class RunPluginAction : AnAction() {
    companion object {
        var n_runs = 0
    }
    override fun actionPerformed(e: AnActionEvent) {
        GlobalStorage.out_path = System.getenv()["OUT_PATH"]
        val project = e.getData(LangDataKeys.PROJECT)!!
        val editor = e.getData(LangDataKeys.EDITOR)!!
        println("Works")
        var openedPopup: ListPopup? = null
        lateinit var myPreviewPopupUpdateProcessor : IntentionPreviewPopupUpdateProcessor



        ProgressManager.getInstance().run(object : Backgroundable(project, "Title") {
            val actionGroup = DefaultActionGroup()
            val channel = LinkedBlockingQueue<AnAction>()
            lateinit var indicator: ProgressIndicator

            fun showPopup(group: DefaultActionGroup) {
                ApplicationManager.getApplication().invokeAndWait {
                    openedPopup?.cancel()
                }
//                Thread.sleep(300)
                ApplicationManager.getApplication().invokeAndWait {
                    openedPopup = JBPopupFactory.getInstance().createActionGroupPopup(
                        "Choose Intention",
                        group,
                        SimpleDataContext.getProjectContext(project),
                        true,
                        null,
                        -1
                    )
                    openedPopup!!.showInBestPositionFor(editor)
                }
            }

            override fun run(progressIndicator: ProgressIndicator) {
                indicator = progressIndicator
                // start your process

                // Set the progress bar percentage and text
                indicator.isIndeterminate = true
                indicator.text = "Prediction started"

                n_runs++
                // 50% done
                val handler = CurrentPositionHandler(e)
                val job = thread {  ActionGroupBuilder().build(handler, channel)  }
                println("After job creation") // For some strange reason this print fixes "first-run-bug"
                val timeout = if (n_runs == 1) 5000L else 1500L
                val action = channel.poll(timeout, TimeUnit.MILLISECONDS) // First action takes more time to load so we wait here
                if (action != null) {
                    actionGroup.addAction(action)
//                greedyGrab(200)
                    steadyGrab(400)
                    job.join()
                    showPopup(actionGroup)
                    myPreviewPopupUpdateProcessor =
                        IntentionPreviewPopupUpdateProcessor(project, handler.file, handler.editor)
                    addPreview()
                } else {
                    indicator.text = "Sorry, no variants found"
                    Thread.sleep(1000)
                }
            }

            fun addPreview() {
                val action: AbstractAction = object : AbstractAction() {
                    override fun actionPerformed(e: ActionEvent) {
                        myPreviewPopupUpdateProcessor.toggleShow()
                        if (openedPopup is ListPopupImpl) {
                            val list = (openedPopup as ListPopupImpl).list
                            val selectedIndex = list.selectedIndex
                            val selectedValue = list.selectedValue
                            if (selectedValue is PopupFactoryImpl.ActionItem) {
                                if (selectedValue.action is ActionWithIntention) {
                                    val intention = (selectedValue.action as ActionWithIntention).intention
                                    IntentionManager.getInstance().addAction(intention)
                                    updatePreviewPopup(
                                        intention,
                                        selectedIndex
                                    )
                                    Thread.sleep(200) // Without this sleep, it unregisters before popup is shown
                                    IntentionManager.getInstance().unregisterIntention(intention)

                                }
                            }
                        }
                    }
                }
                (openedPopup as WizardPopup).registerAction(
                    "showIntentionPreview",
                    KeymapUtil.getKeyStroke(IntentionPreviewPopupUpdateProcessor.getShortcutSet()), action
                )
                openedPopup!!.setAdText(CodeInsightBundle.message(
                        "intention.preview.adv.show.text",
                        getShortcutText()
                    ), SwingConstants.LEFT
                )
            }

            private fun updatePreviewPopup(action: IntentionAction, index: Int) {
                ApplicationManager.getApplication().assertIsDispatchThread()
                myPreviewPopupUpdateProcessor.setup({ text: String? ->
                    ApplicationManager.getApplication().assertIsDispatchThread()
                    openedPopup!!.setAdText(text, SwingConstants.LEFT)
                    Unit
                }, index)
                myPreviewPopupUpdateProcessor.updatePopup(action)
            }

            fun greedyGrab(interval: Long) { // Takes all every interval
                while (true) {
                    Thread.sleep(interval)
                    if (channel.isEmpty()) break
                    while (channel.isNotEmpty()) {
                        val action = channel.take()
                        actionGroup.addAction(action)
                    }
                    showPopup(actionGroup)
                    indicator.text = "${actionGroup.childrenCount} actions processed"
                }
            }

            fun steadyGrab(interval: Long) { // Takes one every interval
                while (true) {
                    Thread.sleep(interval)
                    if (channel.isEmpty()) break
                    val action = channel.take()
                    actionGroup.addAction(action)
                    showPopup(actionGroup)
                    indicator.text = "${actionGroup.childrenCount} actions processed"
                }
            }
        })

    }


}