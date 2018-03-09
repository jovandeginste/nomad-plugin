package org.jenkinsci.plugins.nomad;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.*;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NomadSlave extends AbstractCloudSlave implements EphemeralNode {

    private static final Logger LOGGER = Logger.getLogger(NomadSlave.class.getName());

    private final NomadCloud cloud;
    private final String namespace;
    private final String token;
    private final Boolean reusable;

    public NomadSlave(
        NomadCloud cloud,
        String name,
        String nodeDescription,
        NomadSlaveTemplate template,
        String labelString,
        hudson.slaves.RetentionStrategy retentionStrategy,
        List<? extends NodeProperty<?>> nodeProperties
    ) throws Descriptor.FormException, IOException {
        super(
            name,
            nodeDescription,
            template.getRemoteFs(),
            template.getNumExecutors(),
            template.getMode(),
            labelString,
            new JNLPLauncher(),
            retentionStrategy,
            nodeProperties
        );

        this.cloud = cloud;
	this.namespace = template.getNamespace();
        this.token = template.getToken();
        this.reusable = template.getReusable();
    }

    @Override
    public Node asNode() {
        return this;
    }

    @Extension
    public static class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Nomad Slave";
        }

        /**
         * We only create these kinds of nodes programatically.
         */
        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new NomadComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener)  {
        LOGGER.log(Level.INFO, "Asking Nomad to deregister slave '" + getNodeName() + "'");
        cloud.Nomad().stopSlave(getNodeName(), this.namespace, this.token);
    }

    public NomadCloud getCloud() {
        return cloud;
    }

    public Boolean getReusable() {
        return reusable;
    }
}
