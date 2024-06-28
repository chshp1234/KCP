package com.csp.plugin.gradle

import com.csp.plugin.artifactId
import com.csp.plugin.groupId
import com.csp.plugin.version
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MyPlugin : KotlinCompilerPluginSupportPlugin {

    private lateinit var project: Project

    override fun apply(target: Project) {
        project = target
        println("apply project=$target")
        super.apply(target)
    }

    override fun getCompilerPluginId(): String {
        println("getCompilerPluginId")
        return groupId
    }

    override fun getPluginArtifact(): SubpluginArtifact {
        println("getPluginArtifact")
        return SubpluginArtifact(groupId, artifactId, version)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        println("applyToCompilation")
        val project = kotlinCompilation.target.project
//        val extension = project.extensions.getByType(MyExtension::class.java) as MyExtension
        //可以获取到 build.gradle 中定义的一些配置，透传到 kotlin plugin 中
        return project.provider { emptyList() }
    }

    /**
     * [isApplicable] is checked against compilations of the project, and if it returns true,
     * then [applyToCompilation] may be called later.
     */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val res = project.plugins.hasPlugin(MyPlugin::class.java)
        println("project has MyPlugin?$res")
        return res
    }
}