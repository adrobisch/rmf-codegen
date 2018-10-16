package io.vrap.codegen.kt.languages.java.plantuml

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.rmf.codegen.kt.rendring.FileProducer

class PlantUmlModule : AbstractModule() {
    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        objectTypeBinder.addBinding().to(PlantUmlDiagramProducer::class.java)
    }
}