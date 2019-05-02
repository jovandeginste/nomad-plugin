package org.jenkinsci.plugins.nomad;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.remoting.Engine;
import hudson.remoting.VirtualChannel;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.EphemeralNode;
import org.kohsuke.stapler.DataBoundConstructor;
import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.concurrent.TimeUnit.MINUTES;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

public class NomadRetentionStrategy extends RetentionStrategy<NomadComputer> implements ExecutorListener {
    private static final Logger LOGGER = Logger.getLogger(NomadRetentionStrategy.class.getName());

    private static int DEFAULT_IDLEMINUTES = 1;

    private int idleMinutes = DEFAULT_IDLEMINUTES;

    @DataBoundConstructor
    public NomadRetentionStrategy(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        if (idleMinutes < 1) {
            idleMinutes = DEFAULT_IDLEMINUTES;
        }
        return idleMinutes;
    }

    @Override
    public long check(@Nonnull NomadComputer c) {
        // When the slave is idle we should disable accepting tasks and check to see if it is already trying to
        // terminate. If it's not already trying to terminate then lets terminate manually.
        if (c.isIdle()) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > MINUTES.toMillis(getIdleMinutes())) {
                LOGGER.log(Level.FINE, "Disconnecting {0}", c.getName());
                done(c);
            }
        }

        // Return one because we want to check every minute if idle.
        return 1;
    }

    @Override
    public void start(NomadComputer c) {
        c.connect(false);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor);
    }

    private void done(Executor executor) {
        final NomadComputer c = (NomadComputer) executor.getOwner();
        Queue.Executable exec = executor.getCurrentExecutable();
        LOGGER.log(Level.FINE, "terminating {0} since {1} seems to be finished", new Object[]{c.getName(), exec});
        done(c);
    }

    private synchronized void done(final NomadComputer c) {
        c.setAcceptingTasks(false); // just in case
        VirtualChannel ch = c.getChannel();
        if (ch != null) {
	    try {
                LOGGER.info("Running SlaveDisconnector");
            	ch.call(new SlaveDisconnector());
            } catch (Exception e) {
                LOGGER.info("Warning: SlaveDisconnector failed");
            }
        }

        Future<?> disconnected = c.disconnect(new NomadOfflineCause());
        // wait a bit for disconnection to avoid stack traces in logs
        try {
            disconnected.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            String msg = String.format("Ignoring error waiting for agent disconnection %s: %s", c.getName(), e.getMessage());
            LOGGER.log(Level.INFO, msg, e);
        }

        LOGGER.info("Disconnected computer for node '" + c.getName() + "'.");

        Computer.threadPoolForRemoting.submit(() -> {
            Queue.withLock( () -> {
                if (c.getNode() != null) {
                    try {
                            c.getNode().terminate();
                            Jenkins.getInstance().removeNode(c.getNode());
                    } catch (InterruptedException e) {
                            LOGGER.info("Warning: Failed to terminate " + c.getName()+ e);
                    } catch (IOException e) {
                            LOGGER.info("Warning: Failed to terminate " + c.getName()+ e);
                    }
                }
            });
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NomadRetentionStrategy that = (NomadRetentionStrategy) o;

        return idleMinutes == that.idleMinutes;
    }

    public NomadRetentionStrategy(String idleMinutes) {
        this.idleMinutes = Integer.parseInt(idleMinutes);
    }

    public static class DescriptorImpl extends Descriptor<hudson.slaves.RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Nomad Retention Strategy";
        }
    }

    private static class SlaveDisconnector extends MasterToSlaveCallable<Void, IOException> {

	private static final Logger LOGGER = Logger.getLogger(SlaveDisconnector.class.getName());

        @Override
        public Void call() throws IOException {
            Engine e = Engine.current();
            // No engine, do nothing.
            if (e == null) {
                return null;
            }
            // Tell the slave JNLP agent to not attempt further reconnects.
            e.setNoReconnect(true);
            LOGGER.log(Level.INFO, "Disabled slave engine reconnects.");
            return null;
        }

    }

}
