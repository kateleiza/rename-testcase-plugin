import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
//import com.intellij.refactoring.actions.BaseRefactoringAction.LOG
import com.intellij.refactoring.actions.BaseRefactoringAction.getPsiElementArray
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import java.io.File

class EditorRenameTestCaseAction: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val dataContext = e.dataContext
        val project = e.project ?: return

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = FileDocumentManager.getInstance().getFile(editor!!.document)!!

        val psiNamedElem = getPsiElementArray(dataContext)[0] as PsiNamedElement
        val origName = psiNamedElem.name!!

        val relativeName = virtualFile.canonicalPath!!.substringAfter("/cases/").removeSuffix(".kt")
        val testCaseDir = getTestDataDirectory(File(virtualFile.path)).resolve(relativeName).resolve(origName)
        val filesList = testCaseDir.walk(FileWalkDirection.BOTTOM_UP).filter { file -> file.name.startsWith(origName) }


        val connection = project.messageBus.connect(project)
        connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object: RefactoringEventListener {
            override fun refactoringDone(p0: String, p1: RefactoringEventData?) {
                val newPsiNamedElem = getPsiElementArray(dataContext)[0] as PsiNamedElement
                val newName = newPsiNamedElem.name!!

                filesList.forEach { file ->
                    val suffix = file.name.removePrefix(origName)
                    assert(FileUtil.rename(file, newName + suffix)) }

                connection.disconnect()
            }

            override fun undoRefactoring(p0: String) {

            }

            override fun conflictsDetected(p0: String, p1: RefactoringEventData) {

            }

            override fun refactoringStarted(p0: String, p1: RefactoringEventData?) {

            }
        })
        RenameElementAction().actionPerformed(e)


        //syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
    }

    private fun getTestDataDirectory(currentDir: File): File {
        val name = "testData"
        val riderTestCasesDir = "rider-test-cases"

        var current = currentDir
        while (current.name != riderTestCasesDir) {
            current = current.parentFile ?: break
        }
        val dir = File(current, name)
        if (dir.isDirectory) {
            println("Found testData directory at $dir")
            return dir
        }

        throw Exception("Can't find testData directory starting from $currentDir and upwards")
    }

}