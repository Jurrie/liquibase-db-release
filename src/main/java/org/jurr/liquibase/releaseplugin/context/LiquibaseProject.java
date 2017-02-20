package org.jurr.liquibase.releaseplugin.context;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jurr.liquibase.releaseplugin.Utils;

public class LiquibaseProject
{
	private final Set<MasterFile> masterFiles = new HashSet<>();
	private final String newVersion;
	private final Set<Path> includeFilesToSkipTagging = new HashSet<>();
	private final List<IncludeFile> includeFiles = new ArrayList<>();
	private final String context;
	private final Path classpathRoot;

	public LiquibaseProject(@Nonnull final String newVersion, final String context, @Nonnull final Path classpathRoot)
	{
		this.newVersion = newVersion;
		this.context = context;
		this.classpathRoot = classpathRoot;
	}

	@CheckForNull
	public String getContext()
	{
		return context;
	}

	@Nonnull
	public Set<MasterFile> getMasterFiles()
	{
		return Collections.unmodifiableSet(masterFiles);
	}

	public void addMasterFile(@Nonnull final MasterFile masterFile)
	{
		masterFile.setLiquibaseProject(this);
		masterFiles.add(masterFile);
	}

	@Nonnull
	public String getNewVersion()
	{
		return newVersion;
	}

	@Nonnull
	public Set<Path> getIncludeFilesToSkipTagging()
	{
		return Collections.unmodifiableSet(includeFilesToSkipTagging);
	}

	public void addIncludeFileToSkipTagging(@Nonnull final MasterFile masterFile, @Nonnull final String uri)
	{
		this.addIncludeFileToSkipTagging(masterFile, Paths.get(uri));
	}

	/**
	 * @param path the include file to skip, relative to the master file given. If you want to use the path as-is, see {@link #addIncludeFileToSkipTagging(Path)}.
	 * @param masterFile the master file that <code>path</code> is relative to.
	 * @see #addIncludeFileToSkipTagging(Path)
	 */
	public void addIncludeFileToSkipTagging(@Nonnull final MasterFile masterFile, @Nonnull final Path path)
	{
		includeFilesToSkipTagging.add(Utils.resolveIncludeFile(masterFile.getPath(), path, true, classpathRoot));
	}

	public void addIncludeFileToSkipTagging(@Nonnull final String uri)
	{
		this.addIncludeFileToSkipTagging(Paths.get(uri));
	}

	/**
	 * @param path the include file to skip. This path is taken as-is; it's <em>not</em> relative to a master file. For that, see {@link #addIncludeFileToSkipTagging(MasterFile, Path)}.
	 * @see #addIncludeFileToSkipTagging(MasterFile, Path)
	 */
	public void addIncludeFileToSkipTagging(@Nonnull final Path path)
	{
		includeFilesToSkipTagging.add(path);
	}

	public void createNewVersion() throws IOException
	{
		for (final MasterFile masterFile : masterFiles)
		{
			masterFile.createNewVersion();
		}

		for (final IncludeFile includeFile : includeFiles)
		{
			includeFile.createNewVersion();
		}
	}

	@Nonnull
	IncludeFile addIncludeFile(@Nonnull final IncludeFile includeFile)
	{
		final int includeFileIndex = includeFiles.indexOf(includeFile);
		if (includeFileIndex == -1)
		{
			includeFile.setLiquibaseProject(this);
			includeFiles.add(includeFile);
			return includeFile;
		}
		else
		{
			return includeFiles.get(includeFileIndex);
		}
	}
}