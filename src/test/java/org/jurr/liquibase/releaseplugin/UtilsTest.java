package org.jurr.liquibase.releaseplugin;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class UtilsTest
{
	@Test
	public void testResolveIncludeFileRelativeToChangeLog()
	{
		final Path changeLogFile = Paths.get("dir", "masterfile.xml");
		final Path includeFile = Paths.get("subdir", "include.xml");
		final Path expectedPath = Paths.get("dir", "subdir", "include.xml");
		final Path actualPath = Utils.resolveIncludeFile(changeLogFile, includeFile, true);
		assertEquals(expectedPath, actualPath);
	}

	@Test
	public void testResolveIncludeFileNotRelativeToChangeLog()
	{
		final Path changeLogFile = Paths.get("dir", "masterfile.xml");
		final Path includeFile = Paths.get("subdir", "include.xml");
		final Path expectedPath = includeFile;
		final Path actualPath = Utils.resolveIncludeFile(changeLogFile, includeFile, false);
		assertEquals(expectedPath, actualPath);
	}

	@Test
	public void testReplaceStringInPath()
	{
		final Path inputPath = Paths.get("abc", "latest", "def", "file-latest-blah.xml");
		final Path expectedPath = Paths.get("abc", "VERSION", "def", "file-VERSION-blah.xml");
		final Path actualPath = Utils.replaceStringInPath(inputPath, "latest", "VERSION");
		assertEquals(expectedPath, actualPath);
	}

	@Test
	public void testConvertPathSeparatorToForwardSlashWindows()
	{
		final Path input = Paths.get("abc\\def");
		final String expected = "abc/def";
		final String actual = Utils.convertPathSeparatorToForwardSlash(input);
		assertEquals(expected, actual);
	}

	@Test
	public void testConvertPathSeparatorToForwardSlashUnix()
	{
		final Path input = Paths.get("abc/def");
		final String expected = "abc/def";
		final String actual = Utils.convertPathSeparatorToForwardSlash(input);
		assertEquals(expected, actual);
	}
}