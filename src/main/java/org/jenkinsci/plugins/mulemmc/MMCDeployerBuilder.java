package org.jenkinsci.plugins.mulemmc;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifact;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link MMCDeployerBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class MMCDeployerBuilder extends Builder {

    private final String mmcUrl;
    private final String user;
    private final String password;
    private final String deploymentScenario;
    private final String versionPattern;
    
    private HttpClient mmcHttpClient;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public MMCDeployerBuilder(String mmcUrl, String user, String password, String deploymentScenario, String versionPattern) {
        this.mmcUrl = mmcUrl;
        this.user = user;
        this.password = password;
        this.deploymentScenario = deploymentScenario;
        this.versionPattern = versionPattern;
    }


    public String getMmcUrl() {
		return mmcUrl;
	}


	public String getUser() {
		return user;
	}


	public String getPassword() {
		return password;
	}

	public String getDeploymentScenario() {
		return deploymentScenario;
	}

	public String getVersionPattern() {
		return versionPattern;
	}


	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.

        // This also shows how you can consult the global configuration of the builder
        listener.getLogger().println(">>> MMC URL IS " + mmcUrl);
        listener.getLogger().println(">>> USER IS " + user);
        listener.getLogger().println(">>> PASSWORD IS " + password);
        listener.getLogger().println(">>> DEPLOYMENT SCENARIO IS " + deploymentScenario);

        final Map<MavenArtifact, File> allMuleApplications = new HashMap<MavenArtifact, File>();

        if (build instanceof MavenModuleSetBuild) {
            for (final List<MavenBuild> mavenBuilds : ((MavenModuleSetBuild) build).getModuleBuilds().values()) {
                for (final MavenBuild mavenBuild : mavenBuilds) {
                	
                	MavenArtifactRecord record = mavenBuild.getMavenArtifacts();
                	
//                	listener.getLogger().println(">>>>>>> IS POM " + record.isPOM());
                	
                	MavenArtifact mainArtifact = record.mainArtifact;
                	
//                	listener.getLogger().println(">>>>>>> ARTIFACT ID: " +  mainArtifact.artifactId);
//                	listener.getLogger().println(">>>>>>> VERSION: " +  mainArtifact.version);
//                	try {
//						listener.getLogger().println(">>>>>>> FILE: " +  mainArtifact.getFile(mavenBuild).getAbsolutePath());
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}

                	if (record.isPOM()) {
                		List<MavenArtifact> attachedArtifacts = record.attachedArtifacts;
                		for (final MavenArtifact nextAttached : attachedArtifacts) {
                			
                			listener.getLogger().println(">>>>>>>>>>>> ARTIFACT ID: " +  nextAttached.artifactId);
                        	listener.getLogger().println(">>>>>>>>>>>> VERSION: " +  nextAttached.version);
                        	try {
        						listener.getLogger().println(">>>>>>>>>>>> FILE: " +  nextAttached.getFile(mavenBuild).getAbsolutePath());
                            	allMuleApplications.put(nextAttached, nextAttached.getFile(mavenBuild));
        					} catch (IOException e) {
        						// TODO Auto-generated catch block
        						e.printStackTrace();
        					}
                        	
                		}                		
                	}                	
                }
            }
        }
        
        if (!allMuleApplications.isEmpty()) {
        	listener.getLogger().println(">>> Pushing MuleApps to MMC...");
        	for (Entry<MavenArtifact, File> e: allMuleApplications.entrySet()) {
        		try {
					String response = deployMuleApp(e.getKey(), e.getValue());
					listener.getLogger().println(">>> Response is: " + response);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					listener.getLogger().println("EXCEPTION: " + e1.toString());
				}
        		
        		
//        		deployMuleApp(allMuleApplications.get(i));
        	}
        }

        return true;
    }

	private String deployMuleApp(MavenArtifact muleApp, File file) throws Exception {
		
        HttpClient httpClient = configureHttpClient();
        
        PostMethod post = new PostMethod(getMmcUrl() + "/repository");
        post.setDoAuthentication(true);
        
        Part[] parts = {
        	new FilePart("file", file),
        	new StringPart("name", muleApp.artifactId),
        	new StringPart("version", new SimpleDateFormat(getVersionPattern()).format(new Date()))
        };
        
        MultipartRequestEntity multipartEntity = new MultipartRequestEntity(parts, post.getParams());        
        post.setRequestEntity(multipartEntity);        

        int statusCode = httpClient.executeMethod(post);

        if (statusCode == 200)
        	return post.getResponseBodyAsString();

        //TODO - better error handling 
        return "";
	}
	
	private HttpClient configureHttpClient() throws Exception {
		if (mmcHttpClient == null) {
			URL url = new URL(getMmcUrl());
		
			mmcHttpClient = new HttpClient();
        
			mmcHttpClient.getState().setCredentials(
        		new AuthScope(url.getHost(), url.getPort()),
                new UsernamePasswordCredentials(getUser(), getPassword()));

	        List authPrefs = new ArrayList(3);
	        authPrefs.add(AuthPolicy.BASIC);
	        mmcHttpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
	        mmcHttpClient.getParams().setAuthenticationPreemptive(true);
		}
		
		return mmcHttpClient;
	}
    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link MMCDeployerBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
//        private boolean useFrench;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
//        public FormValidation doCheckName(@QueryParameter String value)
//                throws IOException, ServletException {
//            if (value.length() == 0)
//                return FormValidation.error("Please set a name");
//            if (value.length() < 4)
//                return FormValidation.warning("Isn't the name too short?");
//            return FormValidation.ok();
//        }

        public FormValidation doTestConnection(@QueryParameter("mmcUrl") final String mmcUrl,
        		@QueryParameter("user") final String user,
                @QueryParameter("password") final String password) throws IOException, ServletException {
        	
        	try {
        		URL url = new URL(mmcUrl);
                HttpClient client = new HttpClient();
                
    			client.getState().setCredentials(
            		new AuthScope(url.getHost(), url.getPort()),
                    new UsernamePasswordCredentials(user, password));

    	        List authPrefs = new ArrayList(3);
    	        authPrefs.add(AuthPolicy.BASIC);
    	        client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
    	        client.getParams().setAuthenticationPreemptive(true);
    	        
    	        GetMethod method = new GetMethod(mmcUrl + "/deployments");
    	        int statusCode = client.executeMethod(method);
    	        
    	        if (statusCode == 200)
    	        	return FormValidation.ok("Success");
    	        else
    	        	return FormValidation.error("Client error : " + method.getStatusText());
            } catch (Exception e) {
                return FormValidation.error("Client error : "+e.getMessage());
            }
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Deploy to Mule Management Console";
        }

//        @Override
//        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
//            // To persist global configuration information,
//            // set that to properties and call save().
//            useFrench = formData.getBoolean("useFrench");
//            // ^Can also use req.bindJSON(this, formData);
//            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
//            save();
//            return super.configure(req,formData);
//        }
        
        
//        public ListBoxModel doFillDeploymentScenariosItems() {
//            ListBoxModel items = new ListBoxModel();
////            for (BuildGoal goal : getBuildGoals()) {
////                items.add(goal.getDisplayName(), goal.getId());
////            }
//            items.add("Option1", "1");
//            items.add("Option2", "2");
//            return items;
//        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
//        public boolean getUseFrench() {
//            return useFrench;
//        }
    }
}

