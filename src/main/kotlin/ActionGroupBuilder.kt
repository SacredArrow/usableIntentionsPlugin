import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import graph.Graph
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import metrics.Metric
import weka.classifiers.Evaluation
import weka.classifiers.trees.RandomForest
import weka.core.*
import weka.core.converters.ArffLoader
import java.io.File
import kotlin.test.assertTrue
import java.util.ArrayList

import weka.core.Instances
import kotlin.system.measureTimeMillis


class ActionGroupBuilder {
    fun build(handler: CurrentPositionHandler) : ActionGroup {

        val actionGroup = DefaultActionGroup()
        val applier = SequentialApplier(handler)
        val time = measureTimeMillis {
            applier.start()
        }
        println(time)
        val codePieces = applier.getCodePieces().toList()
        if (codePieces.isEmpty()) return actionGroup
        val graph = Graph()
        graph.build(applier.events)
        graph.bfs(true)
        val predictions = Predictor(graph).predictForCodePieces(codePieces).toList().sortedByDescending { it.second } // Good predictions in the beginning
        for ((codePiece, prediction) in predictions) {
            val pathId = graph.nodes[codePiece.hash]!!.pathIndex
            val path = Graph.Mappings.indexToPathMapping[pathId]!!
            val name = path.first.drop(1).joinToString()
            val action = object : AnAction(name) {
                override fun actionPerformed(e: AnActionEvent) {
//                    for (name in path.first.drop(1)) {
//                        applier.runWriteCommandAndCommit {
//                            IntentionManagerImpl().availableIntentions.filter { it.familyName == name }[0].invoke(
//                                handler.project,
//                                handler.editor,
//                                handler.file
//                            )
//                        }
//                    }
                    IntentionManager.getInstance().addAction(object: IntentionAction {
                        override fun startInWriteAction(): Boolean {
                            return true
                        }

                        override fun getText(): String {
                            return name
                        }

                        override fun getFamilyName(): String {
                            return "Some family text"
                        }

                        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                            return true
                        }

                        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                            for (name in path.first.drop(1)) {
                                    IntentionManagerImpl().availableIntentions.filter { it.familyName == name }[0].invoke(
                                        project,
                                        editor,
                                        file
                                    )
                            }
                        }

                    })
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = prediction == 1
                }
            }
            actionGroup.add(action)
        }
        return actionGroup
    }
}