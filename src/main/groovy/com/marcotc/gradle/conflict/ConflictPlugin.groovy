package com.marcotc.gradle.conflict

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RelativePath
import groovy.transform.ToString
import com.google.common.collect.TreeMultimap
import com.google.common.base.Joiner
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.Action

// TODO Fix this whole mess
public class ConflictPlugin implements Plugin<Project> {
	static final String GROUP = 'conflict'
    static final String DESC = 'Show files from dependencies that have the same path'
	
	@ToString
	public static class Resource {
		File source;
		Object dependency;
		Collection<File> relativePaths;
	}
	
    void apply(Project project) {
        def task = project.task('conflict') << {
			FileCollection files = project.files([])
			List<Resource> things = [];
			
			def conf = project.configurations.runtime
			
			conf.getResolutionStrategy().eachDependency(new Action<DependencyResolveDetails>() {
				@Override
				public void execute(DependencyResolveDetails details) {
					things += new Resource(dependency: details.getTarget())
				}
			});
			
			def i = 0
			conf.collect { f ->
				def depTree = f.isDirectory() ? project.fileTree(f) : project.zipTree(f)
				def depFiles = []
				depTree.visit {
					if (!it.isDirectory()) {
						depFiles += it.getRelativePath()
					}
				}
				
				def dep = things[i++]
				dep.source = f
				dep.relativePaths = depFiles
			}
			
			if (project.sourceSets.main.output.resourcesDir?.exists()) {
				Set<File> paths = [] + project.sourceSets.main.output.resourcesDir
				
				paths.each {
					def depFiles = []
					project.fileTree(it).visit{
						depFiles += it.isDirectory() ? [] : it.getRelativePath()
					}
					things += new Resource(source: it, relativePaths: depFiles, dependency:project.sourceSets.main.resources.name)
				}
			}
			if (project.sourceSets.main.output.classesDir?.exists()) {
				Set<File> paths = [] + project.sourceSets.main.output.classesDir
				
				paths.each {
					def depFiles = []
					project.fileTree(it).visit{
						depFiles += it.isDirectory() ? [] : it.getRelativePath()
					}
					things += new Resource(source: it, relativePaths: depFiles, dependency:project.sourceSets.main.java.name)
				}
			}
			
			def comparator1 = new Comparator<RelativePath>() {
				public int compare(RelativePath o1, RelativePath o2) {
					return o1.getPathString().compareTo(o2.getPathString())
				}
			};
			
			def comparator2 = new Comparator<Resource>() {
				public int compare(Resource o1, Resource o2) {
					return o1.source.getCanonicalPath().compareTo(o2.source.getCanonicalPath())
				}
			};
			
			def map = TreeMultimap.create(comparator1, comparator2)
			things.each { resource ->
				resource.relativePaths.each {
					map.put(it, resource)
				}
			}
			
			map.asMap().entrySet().each {
				def sources = it.getValue()
				if (sources.size() > 1) {
					def file = it.getKey()
					def fileStr = file.toString()
					def classStr = ".class"
					if (fileStr.endsWith(classStr)) {
						fileStr += " [" + fileStr.substring(0, fileStr.length() - classStr.length()).replace('/','.') + "]"
					}
					
					println ""
					println "File:\t" + fileStr
					println "Sources: "
					sources.each {
						println "\t" + it.dependency.toString() + ": " + it.source.toString()
					}
				}
			}
		}
		
		task.dependsOn project.tasks.classes
		
		def alias = project.task('conflicts') << {
		}
		alias.dependsOn task
		
		task.group = GROUP
        task.description = DESC
		alias.group = GROUP
		alias.description = DESC
    }
}