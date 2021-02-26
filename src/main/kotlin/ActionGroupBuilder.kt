import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.ExtensionPointName
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




class ActionGroupBuilder {
    fun build(handler: CurrentPositionHandler) : ActionGroup {

        val actionGroup = DefaultActionGroup()
        val applier = SequentialApplier(handler)
        applier.start()
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
                    for (name in path.first.drop(1)) {
                        applier.runWriteCommandAndCommit {
                            IntentionManagerImpl().availableIntentions.filter { it.familyName == name }[0].invoke(
                                handler.project,
                                handler.editor,
                                handler.file
                            )
                        }
                    }
//                    val dataSet_train = getDataSet("/home/custos/Projects/Diploma/trash/train.arff")
//                    val dataSet_test = getDataSet("/home/custos/Projects/Diploma/trash/test.arff")
//                    val eval = Evaluation(dataSet_train)
//                    eval.evaluateModel(rf, dataSet_test)
//                    println("** Decision Tress Evaluation with Datasets **");
//                    println(eval.toSummaryString());
//                    print(" the expression for the input data as per alogorithm is ");
//                    println(rf);
//                    println(eval.toMatrixString());
//                    println(eval.toClassDetailsString());
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