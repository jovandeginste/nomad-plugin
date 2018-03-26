package org.jenkinsci.plugins.nomad;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.filter;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static com.cloudbees.plugins.credentials.domains.URIRequirementBuilder.fromUri;
import static org.apache.commons.lang.StringUtils.trimToEmpty;

public class NomadSlaveTemplate implements Describable<NomadSlaveTemplate> {

    private static final String SLAVE_PREFIX = "jenkins-";
    private static final Logger LOGGER = Logger.getLogger(NomadSlaveTemplate.class.getName());

    private final int idleTerminationInMinutes;
    private final Boolean reusable;
    private final int numExecutors;

    private final String namespace;
    private final String nomadTokenCredentialsId;
    private final int cpu;
    private final int memory;
    private final int disk;
    private final int priority;
    private final String labels;
    private final List<? extends NomadConstraintTemplate> constraints;
    private final String region;
    private final String remoteFs;
    private final String image;
    private final Boolean privileged;
    private final String network;
    private final String username;
    private final String password;
    private final String prefixCmd;
    private final Boolean forcePull;
    private final String hostVolumes;
    private final String switchUser;
    private final Node.Mode mode;

    private NomadCloud cloud;
    private String driver;
    private String datacenters;
    private Set<LabelAtom> labelSet;

    @DataBoundConstructor
    public NomadSlaveTemplate(
            String namespace,
            String nomadTokenCredentialsId,
            String cpu,
            String memory,
            String disk,
            String labels,
            List<? extends NomadConstraintTemplate> constraints,
            String remoteFs,
            String idleTerminationInMinutes,
            Boolean reusable,
            String numExecutors,
            Node.Mode mode,
            String region,
            String priority,
            String image,
            String datacenters,
            String username,
            String password,
            Boolean privileged,
            String network,
            String prefixCmd,
            Boolean forcePull,
            String hostVolumes,
            String switchUser
    ) {
        this.namespace = namespace;
        this.nomadTokenCredentialsId = nomadTokenCredentialsId;
        this.cpu = Integer.parseInt(cpu);
        this.memory = Integer.parseInt(memory);
        this.disk = Integer.parseInt(disk);
        this.priority = Integer.parseInt(priority);
        this.idleTerminationInMinutes = Integer.parseInt(idleTerminationInMinutes);
        this.reusable = reusable;
        this.numExecutors = Integer.parseInt(numExecutors);
        this.mode = mode;
        this.remoteFs = remoteFs;
        this.labels = Util.fixNull(labels);
        if (constraints == null) {
            this.constraints = Collections.emptyList();
        } else {
            this.constraints = constraints;
        }
        this.labelSet = Label.parse(labels);
        this.region = region;
        this.image = image;
        this.datacenters = datacenters;
        this.username = username;
        this.password = password;
        this.privileged = privileged;
        this.network = network;
        this.prefixCmd = prefixCmd;
        this.forcePull = forcePull;
        this.hostVolumes = hostVolumes;
        this.switchUser = switchUser;

        readResolve();
    }

    protected Object readResolve() {
        this.driver = !this.image.equals("") ? "docker" : "java";
        return this;
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<NomadSlaveTemplate> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Nomad slave template";
        }

        public ListBoxModel doFillNomadTokenCredentialsIdItems(@QueryParameter String apiUrl,
                                                               @QueryParameter String credentialsId) {
            if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM,
                            Jenkins.getInstance(),
                            StringCredentials.class,
                            fromUri(apiUrl).build(),
                            CredentialsMatchers.always()
                    );
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<NomadSlaveTemplate> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public String createSlaveName() {
        return SLAVE_PREFIX + Long.toHexString(System.nanoTime());
    }

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public Node.Mode getMode() {
        return mode;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getNomadTokenCredentialsId() {
        return nomadTokenCredentialsId;
    }

    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

    public String getLabels() {
        return labels;
    }

    public List<NomadConstraintTemplate> getConstraints() {
        return Collections.unmodifiableList(constraints);
    }

    public int getIdleTerminationInMinutes() {
        return idleTerminationInMinutes;
    }

    public Boolean getReusable() {
        return reusable;
    }

    public String getRegion() {
        return region;
    }

    public String getDatacenters() {
        return datacenters;
    }

    public int getPriority() {
        return priority;
    }

    public int getDisk() {
        return disk;
    }

    public void setCloud(NomadCloud cloud) {
        this.cloud = cloud;
    }

    public NomadCloud getCloud() {
        return cloud;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public String getImage() {
        return image;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPrefixCmd() {
        return prefixCmd;
    }

    public String getDriver() {
        return driver;
    }

    public Boolean getPrivileged() {
        return privileged;
    }

    public String getNetwork() {
        return network;
    }

    public Boolean getForcePull() {
        return forcePull;
    }

    public String getHostVolumes() {
        return hostVolumes;
    }

    public String getSwitchUser() {
        return switchUser;
    }

    public String getTokenValue() {
        return secretFor(this.nomadTokenCredentialsId);
    }


    @Nonnull
    private static String secretFor(String credentialsId) {
        List<StringCredentials> creds = filter(
                lookupCredentials(StringCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                withId(trimToEmpty(credentialsId))
        );
        return creds.get(0).getSecret().getPlainText();
    }

}
