package zielu.gittoolbox.blame;

import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.text.DateFormatUtil;
import git4idea.i18n.GitBundle;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zielu.gittoolbox.ResBundle;

public final class FileBlame extends AbstractBlame {
  private final String author;
  private final Date date;
  private final String detailedText;

  private FileBlame(String author, Date date, String detailedText) {
    this.author = prepareAuthor(author);
    this.date = date;
    this.detailedText = detailedText;
  }

  public static Blame create(@NotNull VcsFileRevision revision) {
    String detailedText = revision.getCommitMessage() + "\n...";
    detailedText = GitBundle.message("annotation.tool.tip", revision.getRevisionNumber().asString(),
        revision.getAuthor(), DateFormatUtil.formatDateTime(revision.getRevisionDate()), detailedText);
    return new FileBlame(revision.getAuthor(), revision.getRevisionDate(), detailedText);
  }

  @NotNull
  @Override
  public String getShortText() {
    return author + " " + DateFormatUtil.formatBetweenDates(date.getTime(), System.currentTimeMillis());
  }

  @Override
  protected String getStatusPrefix() {
    return ResBundle.getString("blame.file.prefix");
  }

  @Nullable
  @Override
  public String getDetailedText() {
    return detailedText;
  }
}
