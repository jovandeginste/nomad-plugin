package org.jenkinsci.plugins.nomad;

import hudson.slaves.OfflineCause;

public class NomadOfflineCause extends OfflineCause {
    @Override
    public String toString() {
        return "Shutting down Nomad container";
    }
}

