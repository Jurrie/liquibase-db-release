package org.jurr.liquibase.releaseplugin;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.jurr.liquibase.releaseplugin.context.LiquibaseProject;
import org.jurr.liquibase.releaseplugin.context.MasterFile;

@Mojo(name = "tag", defaultPhase = LifecyclePhase.NONE)
public class TagDatabaseMojo extends AbstractMojo
{
	/**
	 * These files are considered 'master files'; tags are placed in these files and files included from these files are copied to a new version variant.
	 */
	@Parameter(required = true)
	private Set<File> masterFiles;

	/**
	 * These files are not tagged with the new version, even if they are included.
	 */
	@Parameter
	private Set<File> skippedIncludeFiles;

	/**
	 * The version that this plugin should create.
	 */
	@Parameter(property = "newVersion")
	private String newVersion;

	/**
	 * The context to set on the tagDatabase changeSet. Default is to not generate a context attribute.
	 */
	@Parameter(property = "context", required = false)
	private String context;

	@Component
	private Prompter prompter;

	@Parameter(property = "settings", readonly = true, required = true)
	private Settings settings;

	@Parameter(property = "classpathRoot", readonly = true, required = true, defaultValue = "${project.build.resources[0].directory}")
	private File classpathRoot;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		retrieveNewVersion();

		final LiquibaseProject liquibaseProject = new LiquibaseProject(newVersion, context, classpathRoot.toPath());

		for (final File masterFile : masterFiles)
		{
			liquibaseProject.addMasterFile(new MasterFile(masterFile.toPath(), classpathRoot.toPath()));
		}

		if (skippedIncludeFiles != null)
		{
			for (final File skippedIncludeFile : skippedIncludeFiles)
			{
				liquibaseProject.addIncludeFileToSkipTagging(skippedIncludeFile.toPath());
			}
		}

		try
		{
			liquibaseProject.createNewVersion();
		}
		catch (IOException e)
		{
			throw new MojoFailureException("IO exception while creating new version", e);
		}
	}

	private void retrieveNewVersion() throws MojoExecutionException
	{
		if (StringUtils.isEmpty(newVersion))
		{
			if (settings.isInteractiveMode())
			{
				try
				{
					newVersion = prompter.prompt("Enter the new version to set");
				}
				catch (PrompterException e)
				{
					throw new MojoExecutionException(e.getMessage(), e);
				}
			}
		}

		if (StringUtils.isEmpty(newVersion))
		{
			throw new MojoExecutionException("You must specify the new version, either by using the newVersion property (that is -DnewVersion=... on the command line) or run in interactive mode");
		}
	}
}