package org.jenkinsci.plugins.mulemmc;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class MMCDeployerBuilder extends Builder {

	public final String mmcUrl;
	public final String user;
	public final String password;
	public final String fileLocation;
	public final String artifactVersion;
	public final String artifactName;
	public final boolean clusterDeploy;
	public final String clusterOrServerGroupName;

	@DataBoundConstructor
	public MMCDeployerBuilder(String mmcUrl
									, String user
									, String password
									//, boolean clusterDeploy
									, String clusterOrServerGroupName
									, String fileLocation
									, String artifactName
									, String artifactVersion ) {
		this.mmcUrl = mmcUrl;
		this.user = user;
		this.password = password;
		this.fileLocation = fileLocation;
		this.artifactName = artifactName;
		this.artifactVersion = artifactVersion;
		this.clusterOrServerGroupName = clusterOrServerGroupName;
		this.clusterDeploy = false;		
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		
		listener.getLogger().println(">>> MMC URL IS " + mmcUrl);
		listener.getLogger().println(">>> USER IS " + user);
		listener.getLogger().println(">>> PASSWORD IS " + password);
		
		MuleRest muleRest = null;
		
		try {
			muleRest = new MuleRest(new URL(mmcUrl), user, password);

			File aFile = new File (fileLocation);
			
			listener.getLogger().println("Deployment starting...");
			String versionId = muleRest.restfullyUploadRepository(artifactName, artifactVersion, aFile);
			String deploymentId=null;
			if(clusterOrServerGroupName!=null && clusterDeploy)
			{ 
				 deploymentId = muleRest.restfullyCreateClusterDeployment(clusterOrServerGroupName, artifactName, versionId);
				
			}else
			{
				 deploymentId = muleRest.restfullyCreateDeployment(clusterOrServerGroupName, artifactName, versionId);

			}
			muleRest.restfullyDeployDeploymentById(deploymentId);
			listener.getLogger().println("Deployment finished");
		} catch (Exception e) {
			listener.getLogger().println("Error in attempting to deploy archive: " + e.toString());
		}
		return true;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link MMCDeployerBuilder}. 
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		

		/**
		 * In order to load the persisted global configuration, you have to call load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		public FormValidation doTestConnection(@QueryParameter("mmcUrl") final String mmcUrl, @QueryParameter("user") final String user,
		        @QueryParameter("password") final String password) throws IOException, ServletException {

			try {
				URL url = new URL(mmcUrl);
				HttpClient client = new HttpClient();

				client.getState().setCredentials(new AuthScope(url.getHost(), url.getPort()),
				        new UsernamePasswordCredentials(user, password));

				List authPrefs = new ArrayList(3);
				authPrefs.add(AuthPolicy.BASIC);
				client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
				client.getParams().setAuthenticationPreemptive(true);

				GetMethod method = new GetMethod(mmcUrl + "/deployments");
				int statusCode = client.executeMethod(method);

				if (statusCode == 200) return FormValidation.ok("Success");
				else return FormValidation.error("Client error : " + method.getStatusText());
			} catch (Exception e) {
				return FormValidation.error("Client error : " + e.getMessage());
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

	public String getFileLocation() {
		return fileLocation;
	}
	
	public String getArtifactName() {
		return artifactName;
	}
	
	public String getArtifactVersion() {
		return artifactVersion;
	}
	
	public Boolean isClusterDeploy() {
		return clusterDeploy;
	}

	public String clusterOrServerGroupName() {
		return clusterOrServerGroupName;
	}
}