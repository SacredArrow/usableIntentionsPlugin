import com.intellij.codeInsight.completion.PrefixMatcher
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


class RunPluginAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        GlobalStorage.out_path = System.getenv()["OUT_PATH"]
        val project = e.getData(LangDataKeys.PROJECT)!!
        val editor = e.getData(LangDataKeys.EDITOR)!!
        println("Works")
        val handler = CurrentPositionHandler(e)
        val actionGroup = ActionGroupBuilder().build(handler)
        val popup = JBPopupFactory.getInstance().createActionGroupPopup("Choose Intention", actionGroup, SimpleDataContext.getProjectContext(project), true, null, -1)
        popup.showInBestPositionFor(editor)

    }


}