package org.eclipse.buildship.core.launch.internal

import groovy.lang.Closure

import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.GradleProjectNature
import org.eclipse.buildship.core.test.fixtures.ProjectSynchronizationSpecification

class GradleClasspathProviderUpdateTest extends ProjectSynchronizationSpecification {

    ILaunchConfiguration launchConfiguration

    def setup() {
        ILaunchConfigurationWorkingCopy launchConfigWorkingCopy = createLaunchConfig(
            SupportedLaunchConfigType.JDT_JAVA_APPLICATION.id,
            'launch config for' + GradleClasspathProviderUpdateTest.class.simpleName)
        launchConfigWorkingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, 'project-name')
        launchConfiguration = launchConfigWorkingCopy.doSave()
    }

    def "Gradle classpath provider added when referenced project is a new Java project"() {
        setup:
        File projectDir = dir('project-name') {
            file 'build.gradle', "apply plugin: 'java'"
        }

        when:
        importAndWait(projectDir)

        then:
        waitFor { hasGradleClasspathProvider(launchConfiguration) }
    }

    def "Gradle classpath provider not added for new non-Java project"() {
        setup:
        File projectDir = dir('project-name')

        when:
        importAndWait(projectDir)

        then:
        waitFor { !hasGradleClasspathProvider(launchConfiguration) }
    }

    def "Gradle classpath provider injected when Gradle project is moved under target name"() {
        setup:
        File settingsFile
        File projectDir = dir('root-project') {
             settingsFile = file 'settings.gradle', 'include "old-name"'
             file 'build.gradle', 'allprojects { apply plugin: "java" }'
             dir('old-name')
             dir('project-name')
        }
        importAndWait(projectDir)

        expect:
        findProject('old-name')
        waitFor { !hasGradleClasspathProvider(launchConfiguration) }

        when:
        settingsFile.text = 'include "project-name"'
        synchronizeAndWait(projectDir)
        waitForResourceChangeEvents()

        then:
        waitFor { hasGradleClasspathProvider(launchConfiguration) }
    }

    def "Gradle classpath provider removed when project deleted"() {
        setup:
        File projectDir = dir('project-name') {
            file 'build.gradle', "apply plugin: 'java'"
        }
        def listener = { IResourceChangeEvent event -> CorePlugin.logger().warn("resource changed:" + event.delta) } as IResourceChangeListener
        workspace.addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE)
        importAndWait(projectDir)
        CorePlugin.logger().warn("project imported")
        expect:
        waitFor { hasGradleClasspathProvider(launchConfiguration) }

        when:
        findProject('project-name').delete(false, new NullProgressMonitor())
        waitForResourceChangeEvents()
        CorePlugin.logger().warn("project deleted")

        then:
        waitFor { !hasGradleClasspathProvider(launchConfiguration) }

        cleanup:
        workspace.removeResourceChangeListener(listener)
    }

    def "Gradle classpath provider added when Gradle nature added"() {
        setup:
        IJavaProject javaProject = newJavaProject('project-name')

        expect:
        waitFor { !hasGradleClasspathProvider(launchConfiguration) }

        when:
        CorePlugin.workspaceOperations().addNature(javaProject.project, GradleProjectNature.ID, new NullProgressMonitor())

        then:
        waitFor { hasGradleClasspathProvider(launchConfiguration) }
    }

    def "Gradle classpath provider removed when Gradle nature removed"() {
        setup:
        File projectDir = dir('project-name') {
            file 'build.gradle', "apply plugin: 'java'"
        }
        importAndWait(projectDir)

        expect:
        waitFor { hasGradleClasspathProvider(launchConfiguration) }

        when:
        CorePlugin.workspaceOperations().removeNature(findProject('project-name'), GradleProjectNature.ID, new NullProgressMonitor())

        then:
        waitFor { !hasGradleClasspathProvider(launchConfiguration) }
    }

    private boolean hasGradleClasspathProvider(ILaunchConfiguration configuration) {
        getClasspathProvider(configuration) == GradleClasspathProvider.ID
    }

    private String getClasspathProvider(ILaunchConfiguration configuration) {
        configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, (String)null)
    }

    protected void waitFor(int timeout = 5000, Closure condition) {
        long start = System.currentTimeMillis()
        while (!condition.call()) {
            long elapsed = System.currentTimeMillis() - start
            if (elapsed > timeout) {
                throw new RuntimeException('timeout')
            }
            Thread.sleep(100)
        }
    }
}
