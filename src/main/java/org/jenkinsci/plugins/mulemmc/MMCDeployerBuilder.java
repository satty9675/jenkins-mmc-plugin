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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * MMCDeployerBuilder {@link Builder}.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class MMCDeployerBuilder extends Builder {

    public final String mmcUrl;
    public final String user;
    public final String password;
    public final Boolean updateDeploymentScenario;
    public final String deploymentScenarioName;
    public final String versionPattern;
    
    private HttpClient mmcHttpClient;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public MMCDeployerBuilder(String mmcUrl, String user, String password,
                              String deploymentScenarioName, String versionPattern,
                              Boolean updateDeploymentScenario,
                              DeploymentScenarioBlock deploymentScenarioBlock) {
        this.mmcUrl = mmcUrl;
        this.user = user;
        this.password = password;
        this.versionPattern = versionPattern;

        this.updateDeploymentScenario = updateDeploymentScenario;

        if (deploymentScenarioBlock != null) {
            this.deploymentScenarioName = deploymentScenarioBlock.deploymentScenarioName;
        } else {
            this.deploymentScenarioName = null;
        }
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

	public String getDeploymentScenarioName() {
		return deploymentScenarioName;
	}

    public Boolean getUpdateDeploymentScenario() {
        return updateDeploymentScenario;
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
        listener.getLogger().println(">>> UPDATE DEPLOYMENT SCENARIO IS " + updateDeploymentScenario);
        listener.getLogger().println(">>> DEPLOYMENT SCENARIO IS " + deploymentScenarioName);

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
//					listener.getLogger().println(">>> Response is: " + response);
					
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

        if (statusCode == 200) {
        	String response = post.getResponseBodyAsString();
            post.releaseConnection();

            System.out.println(">>>>>>>>>> UPLOAD TO REPOSITORY: " + response);

        	JSONObject responseJson =  JSONObject.fromObject(response);
			
			String versionId = responseJson.getString("versionId");
			String applicationId = responseJson.getString("applicationId");

            //Check if redeployment is toggled

            redeployNewAppVersion(applicationId, versionId);



        } else {
            System.out.println(">>> POST " + post.getStatusText());
            post.releaseConnection();

        }


        //TODO - better error handling 
        return "";
	}

    private void redeployNewAppVersion(String applicationId, String versionId) throws Exception {
        HttpClient httpClient = configureHttpClient();

        GetMethod get = new GetMethod(getMmcUrl() + "/deployments");
        get.setDoAuthentication(true);

        //List all deployments
        int statusCode = httpClient.executeMethod(get);

        if (statusCode == 200) {
            String response = get.getResponseBodyAsString();
            get.releaseConnection();
            System.out.println(">>>>>>>>>> ALL DEPLOYMENTS: " + response);
            //Here we need to match deployment to application
            JSONObject allDeployments =  JSONObject.fromObject(response);
            JSONArray allDeploymentsData = allDeployments.getJSONArray("data");

            JSONArray allApplicationVersions = listAllApplicationVersions(applicationId);

            for (JSONObject nextDeployment : (List<JSONObject>)JSONArray.toCollection(allDeploymentsData, JSONObject.class)) {
                JSONArray nextDeploymentApps = nextDeployment.getJSONArray("applications");
                String nextDeploymentId = nextDeployment.getString("id");

                for (JSONObject nextAppVersion : (List<JSONObject>) JSONArray.toCollection(allApplicationVersions, JSONObject.class)) {
                    String nextAppVersionId = nextAppVersion.getString("id");

                    if (nextDeploymentApps.contains(nextAppVersionId)) { //Found deployment, Update with new version

                        JSONObject updatedDeployment = new JSONObject();
                        updatedDeployment.put("name", nextDeployment.get("name"));
                        updatedDeployment.put("lastModified", nextDeployment.get("lastModified"));

                        JSONArray apps = new JSONArray();
                        apps.add(nextAppVersionId);
                        updatedDeployment.put("applications", apps);

                        JSONObject removedD = update(nextDeploymentId, updatedDeployment, "remove");

                        apps = new JSONArray();
                        apps.add(versionId);
                        updatedDeployment.put("applications", apps);
                        updatedDeployment.put("lastModified", removedD.get("lastModified"));
                        JSONObject addedD = update(removedD.getString("id"), updatedDeployment, "add");

                        String redeploy = toggleRedeploy(addedD.getString("id"));
                        System.out.println(">>> REDEPLOY : " + redeploy);
                    }

                }
            }
        } else {
            System.out.println(">>> GET " + get.getStatusText());
            get.releaseConnection();
        }
    }

    private JSONObject update(String deploymentId, JSONObject updatedDeployment, String op) throws Exception {
        System.out.println(">>>>>>>>> UPDATE: " + op);
        System.out.println(">>>>>>>>> REQUEST: " + updatedDeployment.toString());

        JSONObject responseObject = null;

        HttpClient httpClient = configureHttpClient();
        PutMethod put = new PutMethod(getMmcUrl() + "/deployments/" + deploymentId + "/" + op);
        put.setDoAuthentication(true);

        System.out.println(">>>>>>>>> URL: " + getMmcUrl() + "/deployments/" + deploymentId + "/" + op);

        put.addRequestHeader("Content-Type", "application/json");
        put.setRequestEntity(new StringRequestEntity(updatedDeployment.toString(), "application/json", null));
        int statusCode = httpClient.executeMethod(put);

        if (statusCode == 200) {
            String response = put.getResponseBodyAsString();
            System.out.println(">>>>>>>>> UPDATE RESPONSE: " + response);
            responseObject = JSONObject.fromObject(response);
        } else {
            System.out.println(">>>>>>>>> UPDATE RESPONSE: " + + statusCode + " " + put.getStatusText());
        }

        put.releaseConnection();

        return responseObject;
    }

    private String toggleRedeploy(String deploymentId) throws Exception {
        String response = "";
        System.out.println(">>>>>>>>> DEPLOYMENT ID: " + deploymentId);

        HttpClient httpClient = configureHttpClient();
        PostMethod post = new PostMethod(getMmcUrl() + "/deployments/" + deploymentId + "/redeploy");
        post.setDoAuthentication(true);

        int statusCode = httpClient.executeMethod(post);

        if (statusCode == 200) {
            response = "OK";
        } else {
            response = "" + statusCode + " " + post.getStatusText();
        }

        post.releaseConnection();

        return response;
    }

    /**
     *
     * @return Application Versions Data:
                [
                    {
                        "name":"20120829-12:50",
                        "id":"local$b7440183-d549-438e-ac5d-1598c9f78b3d",
                        "parentPath":"/Applications/mule-example-echo"
                    },
                    {
                        "name":"20120829-15:30",
                        "id":"local$66b3cf20-6e76-4fd9-8dc6-a50a804069a0",
                        "parentPath":"/Applications/mule-example-hello"
                    }
                ]
     * @throws Exception
     */
    private JSONArray listAllApplicationVersions(String applicationId) throws Exception {

        JSONArray allApplicationVersions = null;

        HttpClient httpClient = configureHttpClient();
        GetMethod get = new GetMethod(getMmcUrl() + "/repository/" + applicationId);
        get.setDoAuthentication(true);

        int statusCode = httpClient.executeMethod(get);

        if (statusCode == 200) {
            String response = get.getResponseBodyAsString();
            allApplicationVersions = JSONObject.fromObject(response).getJSONArray("data");
        }

        get.releaseConnection();

        return allApplicationVersions;
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

    public static class DeploymentScenarioBlock
    {
        private String deploymentScenarioName;

        @DataBoundConstructor
        public DeploymentScenarioBlock(String deploymentScenarioName)
        {
            this.deploymentScenarioName = deploymentScenarioName;
        }
    }

}

