import com.intellij.openapi.extensions.ExtensionPointName
import graph.Graph
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import metrics.Metric
import weka.classifiers.trees.RandomForest
import weka.core.Attribute
import weka.core.DenseInstance
import weka.core.Instances
import weka.core.SerializationHelper
import java.io.File
import java.util.ArrayList
import kotlin.test.assertTrue

class Predictor(private val graph: Graph) {

    private val epName : ExtensionPointName<Metric> = ExtensionPointName.create("org.jetbrains.plugins.template.metricsExtensionPoint")
    val rf = SerializationHelper.read(this::class.java.classLoader.getResource("model.weka").openStream()) as RandomForest;
    private var metricsOriginal : Map<String, Float?> = mapOf() // May be this should be written better
    private val encoding : Map<Int, Int> = Json.decodeFromString(this::class.java.classLoader.getResource("encoding.json").readText()) // Shows place in OneHotEncoder
    private val nFeatures = encoding.size + epName.extensionList.size * 3


    private fun predictForCodePiece(codePiece: CodePiece) : Int {
        val atts = ArrayList<Attribute>(nFeatures + 1)
        val inst = DenseInstance(nFeatures + 1)
        val metrics = MetricsCalculator().calculateForCodePiece(codePiece, epName)
        println(metrics)
        val pathId = graph.nodes[codePiece.hash]!!.pathIndex
        assertTrue { pathId != -1 } // Trying to find a bug
        println(Graph.Mappings.indexToPathMapping[pathId])
        if (pathId == 1) return -1
        @Suppress("ReplaceManualRangeWithIndicesCalls")
        for (i in 0 until encoding.size) {
            atts.add(Attribute(i.toString()))
            if (i == encoding[pathId]) {
                inst.setValue(i, 1.0)
            } else {
                inst.setValue(i, 0.0)
            }
        }
        var ix = encoding.size
        val metricsChanged = MetricsCalculator().calculateForCodePiece(codePiece, epName)
        for ((key, value) in metricsOriginal) {
            atts.add(Attribute(key + "_x"))
            if (value == null) {
                inst.setValue(ix, -1.0)
            } else {
                inst.setValue(ix, value.toDouble())
            }
            ix++
        }
        for ((key, value) in metricsChanged) {
            atts.add(Attribute(key + "_y"))
            if (value == null) {
                inst.setValue(ix, -1.0)
            } else {
                inst.setValue(ix, value.toDouble())
            }
            ix++
        }
        @Suppress("ReplaceManualRangeWithIndicesCalls")
        for (i in 0 until metricsOriginal.size) {
            var originalValue = metricsOriginal.values.toList()[i]
            if (originalValue == null) originalValue = (-1.0).toFloat()
            var changedValue = metricsChanged.values.toList()[i]
            if (changedValue == null) changedValue = (-1.0).toFloat()
            val name = metricsOriginal.keys.toList()[i] + "_diff"
            atts.add(Attribute(name))
//                if ( originalValue == null || changedValue == null) {
//                    inst.setValue(ix, -1.0)
//                } else {
//                    inst.setValue(ix, (originalValue - changedValue).toDouble())
//                }
            inst.setValue(ix, (originalValue - changedValue).toDouble())
            ix++
        }
        val fvClassVal = ArrayList<String>(2)
        fvClassVal.add("False")
        fvClassVal.add("True")
        val myClass = Attribute("ScheduledFirst", fvClassVal)
        atts.add(myClass)
        val dataSet = Instances("TestInstances", atts, 0)
        dataSet.add(inst)
        dataSet.setClassIndex(dataSet.numAttributes() - 1);
        inst.setDataset(dataSet)
//        for (el in rf.distributionForInstance(inst)) {
//            println(el)
//        }
        return rf.classifyInstance(inst).toInt()
    }
    fun predictForCodePieces(codePieces: Collection<CodePiece>) : Map<CodePiece, Int> {
        metricsOriginal = MetricsCalculator().calculateForCodePiece(codePieces.first(), epName)
        val predictions = mutableMapOf<CodePiece, Int>()
        for (codePiece in codePieces) {

//             0 is false and 1 is true
            val prediction = predictForCodePiece(codePiece)
//            if (prediction != -1) predictions.add(prediction)
            predictions[codePiece] = prediction
        }
        return predictions
    }
}