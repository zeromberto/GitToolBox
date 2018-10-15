package zielu.gittoolbox.blame;

import com.intellij.openapi.vcs.actions.ShortNameType;
import com.intellij.openapi.vcs.actions.ShowShortenNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AbstractBlame implements Blame {
  @Nullable
  protected final String prepareAuthor(@Nullable String author) {
    return ShortNameType.shorten(author, ShowShortenNames.getType());
  }

  protected abstract String getStatusPrefix();

  @NotNull
  @Override
  public String getShortStatus() {
    return getStatusPrefix() + " " + getShortText();
  }
}
