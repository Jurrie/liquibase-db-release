package org.jurr.liquibase.releaseplugin.exceptions;

import java.nio.file.Path;

import javax.annotation.Nonnull;

public class VersionAlreadyTaggedException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	private final String version;
	private final Path masterFile;

	public VersionAlreadyTaggedException(@Nonnull final String version, @Nonnull final Path masterFile)
	{
		this.version = version;
		this.masterFile = masterFile;
	}

	@Nonnull
	public String getVersion()
	{
		return version;
	}

	@Nonnull
	public Path getMasterFile()
	{
		return masterFile;
	}

	@Override
	public String getMessage()
	{
		return "Version " + version + " already exists in file " + masterFile;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + (masterFile == null ? 0 : masterFile.hashCode());
		result = prime * result + (version == null ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		VersionAlreadyTaggedException other = (VersionAlreadyTaggedException) obj;
		if (masterFile == null)
		{
			if (other.masterFile != null)
			{
				return false;
			}
		}
		else if (!masterFile.equals(other.masterFile))
		{
			return false;
		}
		if (version == null)
		{
			if (other.version != null)
			{
				return false;
			}
		}
		else if (!version.equals(other.version))
		{
			return false;
		}
		return true;
	}
}