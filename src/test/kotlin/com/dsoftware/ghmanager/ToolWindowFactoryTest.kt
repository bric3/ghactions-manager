package com.dsoftware.ghmanager

import com.dsoftware.ghmanager.ui.settings.GithubActionsManagerSettings
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBPanelWithEmptyText
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import junit.framework.TestCase
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.util.GHCompatibilityUtil
import javax.swing.JPanel


class ToolWindowFactoryTest : GitHubActionsManagerBaseTest() {
    private lateinit var requestExecutorfactoryMock: GithubApiRequestExecutor.Factory
    override fun setUp() {
        super.setUp()
        requestExecutorfactoryMock = mockk<GithubApiRequestExecutor.Factory> {
            every { create(token = any()) } throws Exception("No executor")
        }
        mockkObject(GithubApiRequestExecutor.Factory)
        every { GithubApiRequestExecutor.Factory.getInstance() } returns requestExecutorfactoryMock
    }

    fun `test Panel No GitHub Account`() {
        mockGhActionsService(emptySet(), emptySet())

        factory.init(toolWindow)
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

        TestCase.assertEquals(1, toolWindow.contentManager.contentCount)
        TestCase.assertEquals("Workflows", toolWindow.contentManager.contents[0].displayName)
        val component = toolWindow.contentManager.contents[0].component
        TestCase.assertTrue(component is JBPanelWithEmptyText)
        val panel = component as JBPanelWithEmptyText

        TestCase.assertEquals("GitHub account not configured and no API Token", panel.emptyText.text)
        val subComponents = panel.emptyText.wrappedFragmentsIterable.map { it as SimpleColoredComponent }.toList()
        TestCase.assertEquals("GitHub account not configured and no API Token", subComponents[0].getCharSequence(true))
        TestCase.assertEquals("Go to GitHub settings", subComponents[1].getCharSequence(true))
        TestCase.assertEquals("Go to GitHub-actions-manager plugin settings", subComponents[2].getCharSequence(true))
        verify {
            requestExecutorfactoryMock.create(token = any()) wasNot Called
        }
    }

    fun `test GitHub Account exists but no repositories configured`() {
        mockGhActionsService(emptySet(), setOf("account1"))

        factory.init(toolWindow)
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

        TestCase.assertEquals(1, toolWindow.contentManager.contentCount)
        val component = toolWindow.contentManager.contents[0].component
        TestCase.assertEquals("Workflows", toolWindow.contentManager.contents[0].displayName)
        TestCase.assertTrue(component is JBPanelWithEmptyText)
        val panel = component as JBPanelWithEmptyText
        TestCase.assertEquals("No git repositories in project", panel.emptyText.text)
        verify {
            requestExecutorfactoryMock.create(token = any()) wasNot Called
        }
    }

    fun `test GitHub Account and repos configured shows repo-panel`() {
        mockkStatic(GHCompatibilityUtil::class)
        every { GHCompatibilityUtil.getOrRequestToken(any(), any()) } returns "token"
        mockGhActionsService(setOf("http://github.com/owner/repo"), setOf("account1"))

        factory.init(toolWindow)
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

        TestCase.assertEquals(1, toolWindow.contentManager.contentCount)
        val content = toolWindow.contentManager.contents[0]
        TestCase.assertEquals("owner/repo", content.displayName)
        TestCase.assertTrue(content.component is JPanel)
        val panel = content.component as JPanel
        TestCase.assertEquals(1, panel.componentCount)
        verify {
            requestExecutorfactoryMock.create(token = any())
        }
    }

    fun `test when using settings custom repos + zero repos`() {
        mockkStatic(GHCompatibilityUtil::class)
        every { GHCompatibilityUtil.getOrRequestToken(any(), any()) } returns "token"
        mockGhActionsService(setOf("http://github.com/owner/repo"), setOf("account1"))
        mockSettingsService(
            GithubActionsManagerSettings(
                customRepos = mutableMapOf(
                    "http://github.com/owner/repo" to
                        GithubActionsManagerSettings.RepoSettings(false, "customName")
                )
            )
        )

        factory.init(toolWindow)
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

        val content = toolWindow.contentManager.contents[0]
        TestCase.assertEquals("Workflows", content.displayName)
        TestCase.assertEquals(1, toolWindow.contentManager.contentCount)
        val component = toolWindow.contentManager.contents[0].component
        TestCase.assertTrue(component is JBPanelWithEmptyText)
        val panel = component as JBPanelWithEmptyText

        TestCase.assertEquals("No repositories configured for GitHub-Actions-Manager", panel.emptyText.text)
        val subComponents = panel.emptyText.wrappedFragmentsIterable.map { it as SimpleColoredComponent }.toList()
        TestCase.assertEquals("No repositories configured for GitHub-Actions-Manager", subComponents[0].getCharSequence(true))
        TestCase.assertEquals("Go to GitHub-actions-manager plugin settings", subComponents[1].getCharSequence(true))
        verify {
            requestExecutorfactoryMock.create(token = any()) wasNot Called
        }
    }
}