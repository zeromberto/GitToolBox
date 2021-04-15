package zielu.gittoolbox.branch

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRepository
import git4idea.util.GitUIUtil
import jodd.util.StringBand
import zielu.gittoolbox.notification.GtNotifier
import zielu.gittoolbox.util.GtUtil
import zielu.gittoolbox.util.Html

internal class BranchCleaner(
  myProject: Project,
  private val toClean: Map<GitRepository, List<OutdatedBranch>>
) : Task.Backgroundable(
  myProject,
  "Deleting outdated branches"
) {
  private val resultList = mutableListOf<DeleteResult>()

  override fun run(indicator: ProgressIndicator) {
    indicator.isIndeterminate = true

    val total = toClean.map { it.value.size }.sum()
    val fraction = (1f / total).toDouble()
    var index = 0

    indicator.isIndeterminate = false
    val git = Git.getInstance()
    toClean.forEach { entry ->
      val repoName = GtUtil.name(entry.key)
      entry.value.forEach { branch ->
        indicator.text = "Delete branch $repoName: ${branch.getName()}"
        val gitResult = git.branchDelete(entry.key, branch.getName(), false)
        resultList.add(DeleteResult(repoName, branch, gitResult))
        index++
        indicator.fraction = fraction * index
      }
    }
    indicator.isIndeterminate = true
    toClean.keys.forEach { it.update() }
  }

  override fun onSuccess() {
    val message = StringBand()
    message.append("Deleted ${resultList.size} outdated branches:")
    resultList.forEach {
      message
        .append(Html.BRX)
        .append(it.repoName)
        .append(": ")
        .append(it.branch.getName())
    }

    GtNotifier.getInstance(project).branchCleanupSuccess(
      GitUIUtil.bold("Branch cleanup done"),
      message.toString()
    )
  }
}

private data class DeleteResult(
  val repoName: String,
  val branch: OutdatedBranch,
  val result: GitCommandResult
)
