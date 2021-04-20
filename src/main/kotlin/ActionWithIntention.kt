import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.AnAction

// We need AnAction in popup and we need IntentionAction to get previews
abstract class ActionWithIntention(name : String) : AnAction(name)  {
    abstract val intention : IntentionAction
}