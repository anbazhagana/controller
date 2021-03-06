/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.mdsal.it.base;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

import java.io.File;
import javax.inject.Inject;
import org.junit.Before;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.OptionUtils;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMdsalTestBase implements BindingAwareProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMdsalTestBase.class);
    private static final String MAVEN_REPO_LOCAL = "maven.repo.local";
    private static final String ORG_OPS4J_PAX_URL_MVN_LOCAL_REPOSITORY = "org.ops4j.pax.url.mvn.localRepository";
    private static final String ETC_ORG_OPS4J_PAX_URL_MVN_CFG = "etc/org.ops4j.pax.url.mvn.cfg";
    private static final String ETC_ORG_OPS4J_PAX_LOGGING_CFG = "etc/org.ops4j.pax.logging.cfg";

    private static final String PAX_EXAM_UNPACK_DIRECTORY = "target/exam";
    private static final String KARAF_DEBUG_PORT = "5005";
    private static final String KARAF_DEBUG_PROP = "karaf.debug";
    private static final String KEEP_UNPACK_DIRECTORY_PROP = "karaf.keep.unpack";
    private static final int REGISTRATION_TIMEOUT = 70000;

    /*
     * Default values for karaf distro type, groupId, and artifactId
     */
    private static final String KARAF_DISTRO_TYPE = "zip";
    private static final String KARAF_DISTRO_ARTIFACTID = "opendaylight-karaf-empty";
    private static final String KARAF_DISTRO_GROUPID = "org.opendaylight.odlparent";

    /*
     * Property names to override defaults for karaf distro artifactId, groupId,
     * version, and type
     */
    private static final String KARAF_DISTRO_VERSION_PROP = "karaf.distro.version";
    private static final String KARAF_DISTRO_TYPE_PROP = "karaf.distro.type";
    private static final String KARAF_DISTRO_ARTIFACTID_PROP = "karaf.distro.artifactId";
    private static final String KARAF_DISTRO_GROUPID_PROP = "karaf.distro.groupId";

    public static final String ORG_OPS4J_PAX_LOGGING_CFG = "etc/org.ops4j.pax.logging.cfg";

    @Inject @Filter(timeout = 60000)
    private BundleContext context;
    @Inject @Filter(timeout = 60000)
    private BindingAwareBroker broker;
    private ProviderContext session = null;

    public ProviderContext getSession() {
        return session;
    }

    public abstract MavenUrlReference getFeatureRepo();

    public abstract String getFeatureName();

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("Session Initiated: {}",session);
        this.session = session;
    }

    @Before
    public void setup() throws Exception {
        long start = System.nanoTime();
        broker.registerProvider(this);
        for (int i = 0; i < REGISTRATION_TIMEOUT; i++) {
            if (session != null) {
                long stop = System.nanoTime();
                LOG.info("Registered session {} with the MD-SAL after {} ns",
                        session,
                        stop - start);
                return;
            } else {
                Thread.sleep(1);
            }
        }
        throw new RuntimeException("Session not initiated after " + REGISTRATION_TIMEOUT + " ms");
    }

    public Option getLoggingOption() {
        Option option = editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                "log4j2.logger.mdsal-it-base.name",
                AbstractMdsalTestBase.class.getPackage().getName());
        option = composite(option, editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                "log4j2.logger.mdsal-it-base.level",
                LogLevel.INFO.name()));
        return option;
    }

    /**
     * Override this method to provide more options to config.
     *
     * @return An array of additional config options
     */
    protected Option[] getAdditionalOptions() {
        return null;
    }

    /**
     * Returns a Log4J logging configuration property name for the given class's package name of the form
     * "log4j.logger.package_name".
     *
     * @deprecated The karaf logging provider is now Log4J2 so logging configurations must conform to the Log4J2 style.
     *     This method is kept for compilation backwards compatibility but will be removed in a future release.
     */
    @Deprecated
    public String logConfiguration(final Class<?> klazz) {
        return "log4j.logger." + klazz.getPackage().getName();
    }

    public String getKarafDistro() {
        String groupId = System.getProperty(KARAF_DISTRO_GROUPID_PROP, KARAF_DISTRO_GROUPID);
        String artifactId = System.getProperty(KARAF_DISTRO_ARTIFACTID_PROP, KARAF_DISTRO_ARTIFACTID);
        String type = System.getProperty(KARAF_DISTRO_TYPE_PROP, KARAF_DISTRO_TYPE);

        return maven().groupId(groupId).artifactId(artifactId).versionAsInProject().type(type).getURL();
    }

    protected Option mvnLocalRepoOption() {
        String mvnRepoLocal = System.getProperty(MAVEN_REPO_LOCAL, "");
        LOG.info("mvnLocalRepo \"{}\"", mvnRepoLocal);
        return editConfigurationFilePut(ETC_ORG_OPS4J_PAX_URL_MVN_CFG, ORG_OPS4J_PAX_URL_MVN_LOCAL_REPOSITORY,
                mvnRepoLocal);
    }

    @Configuration
    public Option[] config() {
        Option[] options = new Option[] {
                when(Boolean.getBoolean(KARAF_DEBUG_PROP))
                        .useOptions(KarafDistributionOption.debugConfiguration(KARAF_DEBUG_PORT, true)),
                karafDistributionConfiguration().frameworkUrl(getKarafDistro())
                        .unpackDirectory(new File(PAX_EXAM_UNPACK_DIRECTORY)).useDeployFolder(false),
                when(Boolean.getBoolean(KEEP_UNPACK_DIRECTORY_PROP)).useOptions(keepRuntimeFolder()),
                features(getFeatureRepo(), getFeatureName()),
                //mavenBundle("org.apache.aries.quiesce", "org.apache.aries.quiesce.api", "1.0.0"), getLoggingOption(),
                mvnLocalRepoOption(),
                configureConsole().ignoreLocalConsole().ignoreRemoteShell(),
                editConfigurationFilePut(ETC_ORG_OPS4J_PAX_LOGGING_CFG, "log4j2.rootLogger.level", "INFO") };
        return OptionUtils.combine(options, getAdditionalOptions());
    }
}
