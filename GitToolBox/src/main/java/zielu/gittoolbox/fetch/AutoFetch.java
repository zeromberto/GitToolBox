package zielu.gittoolbox.fetch;

import com.google.common.collect.Lists;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import zielu.gittoolbox.ConfigNotifier;
import zielu.gittoolbox.GitToolBoxApp;
import zielu.gittoolbox.GitToolBoxConfigForProject;

public class AutoFetch extends AbstractProjectComponent {
    private final Logger LOG = Logger.getInstance(getClass());

    private final AtomicLong myLastAutoFetch = new AtomicLong();
    private final AtomicBoolean myActive = new AtomicBoolean();
    private MessageBusConnection myConnection;

    private ScheduledExecutorService myExecutor;
    private final List<ScheduledFuture<?>> myScheduledTasks = new LinkedList<>();
    private int currentInterval;

    public AutoFetch(Project project) {
        super(project);
    }

    public static AutoFetch getInstance(@NotNull Project project) {
        return project.getComponent(AutoFetch.class);
    }

    @Override
    public void initComponent() {
        myConnection = myProject.getMessageBus().connect();
        myConnection.subscribe(ConfigNotifier.CONFIG_TOPIC, new ConfigNotifier.Adapter() {
            @Override
            public void configChanged(Project project, GitToolBoxConfigForProject config) {
                onConfigChange(config);
            }
        });
        myConnection.subscribe(AutoFetchNotifier.TOPIC, this::onStateChanged);
    }

    public static AutoFetch create(Project project) {
        return new AutoFetch(project);
    }

    private void init() {
        GitToolBoxConfigForProject config = GitToolBoxConfigForProject.getInstance(project());
        if (config.autoFetch) {
            synchronized (this) {
                currentInterval = config.autoFetchIntervalMinutes;
                scheduleInitTask();
            }
        }
    }

    private void cancelCurrentTasks() {
        synchronized (this) {
            List<ScheduledFuture<?>> tasks = Lists.newArrayList(myScheduledTasks);
            myScheduledTasks.clear();
            tasks.forEach(t -> t.cancel(false));
        }
    }

    private boolean cleanAndCheckTasks() {
        synchronized (this) {
            myScheduledTasks.removeIf(task -> task.isCancelled() || task.isDone());
            return myScheduledTasks.isEmpty();
        }
    }

    private void onConfigChange(GitToolBoxConfigForProject config) {
        if (config.autoFetch) {
            LOG.debug("Auto-fetch enabled");
            synchronized (this) {
                if (currentInterval != config.autoFetchIntervalMinutes) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Auto-fetch interval or state changed: enabled="
                            + config.autoFetch + ", interval=" + config.autoFetchIntervalMinutes);
                    }

                    cancelCurrentTasks();
                    LOG.debug("Existing task cancelled on auto-fetch change");
                    if (currentInterval == 0) {
                        //enable
                        scheduleFastTask();
                    } else {
                        scheduleTask();
                    }
                    currentInterval = config.autoFetchIntervalMinutes;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Auto-fetch interval and state did not change: enabled="
                            + config.autoFetch + ", interval=" + config.autoFetchIntervalMinutes);
                    }
                }
            }
        } else {
            LOG.debug("Auto-fetch disabled");
            synchronized (this) {
                cancelCurrentTasks();
                currentInterval = 0;
                LOG.debug("Existing task cancelled on auto-fetch disable");
            }
        }
    }

    private void scheduleInitTask() {
        scheduleFastTask(30);
    }

    private void scheduleFastTask() {
        scheduleFastTask(60);
    }

    private void scheduleFastTask(int seconds) {
        if (isActive()) {
            synchronized (this) {
                if (cleanAndCheckTasks()) {
                    LOG.debug("Scheduling fast auto-fetch in ", seconds, " seconds");
                    myScheduledTasks.add(myExecutor.schedule(AutoFetchTask.create(this), seconds, TimeUnit.SECONDS));
                } else {
                    LOG.debug("Tasks already scheduled (in fast auto-fetch)");
                }
            }
        }
    }

    private void onStateChanged(AutoFetchState state) {
        if (state.canAutoFetch()) {
            long lastAutoFetch = lastAutoFetch();
            if (lastAutoFetch != 0) {
                long nextAutoFetch = lastAutoFetch + TimeUnit.MINUTES.toMillis(getIntervalMinutes());
                long difference = nextAutoFetch - System.currentTimeMillis();
                if (difference > 0) {
                    int delayMinutes = Math.max((int) TimeUnit.MILLISECONDS.toMinutes(difference), 1);
                    scheduleTask(delayMinutes);
                } else {
                    scheduleTask(1);
                }
            }
        }
    }

    private int getIntervalMinutes() {
        return GitToolBoxConfigForProject.getInstance(project()).autoFetchIntervalMinutes;
    }

    private void scheduleTask() {
        scheduleTask(getIntervalMinutes());
    }

    private void scheduleTask(int delayMinutes) {
        if (isActive()) {
            synchronized (this) {
                if (cleanAndCheckTasks()) {
                    LOG.debug("Scheduling regular auto-fetch in ", delayMinutes, "  minutes");
                    myScheduledTasks.add(myExecutor.schedule(AutoFetchTask.create(this), delayMinutes, TimeUnit.MINUTES));
                } else {
                    LOG.debug("Tasks already scheduled (in regular auto-fetch)");
                }
            }
        }
    }

    void scheduleNextTask() {
        synchronized (this) {
            GitToolBoxConfigForProject config = GitToolBoxConfigForProject.getInstance(project());
            if (config.autoFetch) {
                scheduleTask();
            }
        }
    }

    public Project project() {
        return myProject;
    }

    boolean isActive() {
        return myActive.get();
    }

    public void updateLastAutoFetchDate() {
        myLastAutoFetch.set(System.currentTimeMillis());
    }

    public long lastAutoFetch() {
        return myLastAutoFetch.get();
    }

    @Override
    public void projectOpened() {
        if (myActive.compareAndSet(false, true)) {
            myExecutor = GitToolBoxApp.getInstance().autoFetchExecutor();
            init();
        }
    }

    @Override
    public void projectClosed() {
        if (myActive.compareAndSet(true, false)) {
            cancelCurrentTasks();
        }
    }

    @Override
    public void disposeComponent() {
        if (myConnection != null) {
            myConnection.disconnect();
            myConnection = null;
        }
    }
}
