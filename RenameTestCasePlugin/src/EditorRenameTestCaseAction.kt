import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
//import com.intellij.refactoring.actions.BaseRefactoringAction.LOG
import com.intellij.refactoring.actions.BaseRefactoringAction.getPsiElementArray
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import java.io.File
import com.intellij.openapi.ui.popup.BalloonBuilder
import java.awt.TrayIcon


class EditorRenameTestCaseAction: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val dataContext = e.dataContext
        val project = e.project ?: return

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = FileDocumentManager.getInstance().getFile(editor!!.document)!!

        val psiNamedElem = getPsiElementArray(dataContext)[0] as PsiNamedElement
        val originalName = psiNamedElem.name!!

        val relativeName = virtualFile.canonicalPath!!.substringAfter("/cases/").removeSuffix(".kt")
        val testCaseDir = getTestDataDirectory(File(virtualFile.path)).resolve(relativeName).resolve(originalName)
        val itemsToRename = testCaseDir.walk(FileWalkDirection.BOTTOM_UP).filter { file -> file.name.startsWith(originalName) }

        val connection = project.messageBus.connect(project)
        connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object: RefactoringEventListener {
            override fun refactoringDone(p0: String, p1: RefactoringEventData?) {
                try {
                    val newPsiElement = p1!!.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) as PsiNamedElement
                    val newName = newPsiElement.name!!

                    itemsToRename.forEach {
                        val suffix = it.name.removePrefix(originalName)
                        val renameTo = newName + suffix

                        if (File(it.parent, renameTo).exists())
                            return@forEach

                        FileUtil.rename(it, renameTo)
                    }
                } finally {
                    connection.disconnect()
                }
            }

            override fun undoRefactoring(p0: String) {
            }

            override fun conflictsDetected(p0: String, p1: RefactoringEventData) {
            }

            override fun refactoringStarted(p0: String, p1: RefactoringEventData?) {
            }
        })

        RenameElementAction().actionPerformed(e)
    }

    private fun getTestDataDirectory(currentDir: File): File {
        var current = currentDir
        while (current.name != "rider-test-cases") {
            current = current.parentFile ?: break
        }

        val testDataDirectory = File(current, "testData")
        if (testDataDirectory.isDirectory) {
            println("Found testData directory at '$testDataDirectory'")
            return testDataDirectory
        }

        throw Exception("Can't find '$testDataDirectory' directory from '$currentDir' and upwards")
    }
}