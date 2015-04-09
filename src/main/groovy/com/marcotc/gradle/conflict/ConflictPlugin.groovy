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

// TODO Fix this whole mess
public class ConflictPlugin implements Plugin<Project> {
	static final String GROUP = 'conflict'
    static final String DESC = 'Show files from dependencies that have the same path'
	
	@ToString
	class Resource {
		File source;
		Collection<File> relativePaths;
	}
	
    void apply(Project project) {
        def task = project.task('conflict') << {
			FileCollection files = project.files([])
			List<Resource> things = [];
			
			Set<File> paths = [] as Set
			if (project.sourceSets.main.output.resourcesDir?.exists())
				paths += project.sourceSets.main.output.resourcesDir
			if (project.sourceSets.main.output.classesDir?.exists())
				paths += project.sourceSets.main.output.classesDir

			paths.each {
				def depFiles = []
				project.fileTree(it).visit{
					depFiles += it.isDirectory() ? [] : it.getRelativePath()
				}
				things += new Resource(source: it, relativePaths: depFiles)
			}
			
			def conf = project.configurations.runtime
			things += conf.collect { f ->
				def depTree = f.isDirectory() ? project.fileTree(f) : project.zipTree(f)
				def depFiles = []
				depTree.visit {
					depFiles += it.getRelativePath()
				}
				
				new Resource(source: f, relativePaths: depFiles)
			}
			
			def comparator1 = new Comparator<RelativePath>() {
				public int compare(RelativePath o1, RelativePath o2) {
					return o1.getPathString().compareTo(o2.getPathString())
				}
			};
			
			def comparator2 = new Comparator<String>() {
				public int compare(String o1, String o2) {
					return o1.compareTo(o2)
				}
			};
			
			def map = TreeMultimap.create(comparator1, comparator2)
			things.each { resource ->
				resource.relativePaths.each {
					map.put(it, resource.source.getCanonicalPath())
				}
			}
			
			map.asMap().entrySet().each {
				def sources = it.getValue()
				if (sources.size() > 1) {
					def file = it.getKey()
					println ""
					println "File: " + file
					println "Sources: " + Joiner.on("\n         ").join(sources)
				}
			}
			
			//println project.getGradle().getGradleUserHomeDir() 
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