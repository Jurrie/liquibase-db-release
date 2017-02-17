package org.jurr.liquibase.releaseplugin;

import static io.takari.maven.testing.TestResources.assertFilesNotPresent;
import static io.takari.maven.testing.TestResources.assertFilesPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.xmlmatchers.XmlMatchers.hasXPath;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nonnull;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.Source;

import io.takari.maven.testing.TestMavenRuntime;
import io.takari.maven.testing.TestResources;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.Test;
import org.jurr.liquibase.releaseplugin.exceptions.VersionAlreadyTaggedException;
import org.xmlmatchers.namespace.SimpleNamespaceContext;
import org.xmlmatchers.transform.XmlConverters;

public class TagDatabaseMojoTest
{
	@Rule
	public final TestResources resources = new TestResources();

	@Rule
	public final TestMavenRuntime maven = new TestMavenRuntime();

	private static final NamespaceContext NS = new SimpleNamespaceContext().withBinding("l", "http://www.liquibase.org/xml/ns/dbchangelog");

	@Test
	public void testIncludedFiles() throws Exception
	{
		final File basedir = resources.getBasedir("testIncludedFiles");
		maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "master.xml")));

		assertFilesPresent(basedir, "1.2.3/include_1.2.3.xml", "latest/include_latest.xml");
		assertFilesPresent(basedir, "component_1.2.3.xml", "component_latest.xml");
		assertFilesPresent(basedir, "master.xml");

		assertThat("1.2.3/include_1.2.3.xml should contain exactly one changeSet", xmlFile(basedir, "1.2.3/include_1.2.3.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS, equalTo("1")));
		assertThat("latest/include_latest.xml should not contain a changeSet", xmlFile(basedir, "latest/include_latest.xml"), not(hasXPath("/l:databaseChangeLog/l:changeSet", NS)));

		assertThat("component_1.2.3.xml should contain exactly one changeSet", xmlFile(basedir, "component_1.2.3.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS, equalTo("1")));
		assertThat("component_latest.xml should not contain a changeSet", xmlFile(basedir, "component_latest.xml"), not(hasXPath("/l:databaseChangeLog/l:changeSet", NS)));

		final Source masterXmlFile = xmlFile(basedir, "master.xml");
		assertThat("master.xml should contain one tagDatabase changeSet", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:changeSet[l:tagDatabase])", NS, equalTo("1")));
		assertThat("master.xml should include 1.2.3/include_1.2.3.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='1.2.3/include_1.2.3.xml'])", NS, equalTo("1")));
		assertThat("master.xml should include latest/include_latest.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='latest/include_latest.xml'])", NS, equalTo("1")));
		assertThat("master.xml should include component_1.2.3.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='component_1.2.3.xml'])", NS, equalTo("1")));
		assertThat("master.xml should include component_latest.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='component_latest.xml'])", NS, equalTo("1")));
	}

	@Test
	public void testIgnoredFiles() throws Exception
	{
		final File basedir = resources.getBasedir("testIgnoredFiles");
		maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "master.xml")), np("skippedIncludeFiles", np("skippedIncludeFile", "ignored_latest_file_1.xml"), np("skippedIncludeFile", "ignored_latest_file_2.xml")));

		assertFilesPresent(basedir, "ignored_latest_file_1.xml", "ignored_latest_file_2.xml");
		assertFilesNotPresent(basedir, "ignored_1.2.3_file_1.xml", "ignored_1.2.3_file_2.xml");
		assertFilesPresent(basedir, "component_1.2.3.xml", "component_latest.xml");
		assertFilesPresent(basedir, "master.xml");

		assertThat("ignored_latest_file_1.xml should contain exactly one changeSet", xmlFile(basedir, "ignored_latest_file_1.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS, equalTo("1")));
		assertThat("ignored_latest_file_2.xml should contain exactly one changeSet", xmlFile(basedir, "ignored_latest_file_2.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS, equalTo("1")));

		assertThat("component_1.2.3.xml should contain exactly one changeSet", xmlFile(basedir, "component_1.2.3.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS, equalTo("1")));
		assertThat("component_latest.xml should not contain a changeSet", xmlFile(basedir, "component_latest.xml"), not(hasXPath("/l:databaseChangeLog/l:changeSet", NS)));

		final Source masterXmlFile = xmlFile(basedir, "master.xml");
		assertThat("master.xml should contain one tagDatabase changeSet", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:changeSet[l:tagDatabase])", NS, equalTo("1")));
		assertThat("master.xml should include ignored_latest_file_1.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='ignored_latest_file_1.xml'])", NS, equalTo("1")));
		assertThat("master.xml should include ignored_latest_file_2.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='ignored_latest_file_2.xml'])", NS, equalTo("1")));
		assertThat("master.xml should include component_1.2.3.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='component_1.2.3.xml'])", NS, equalTo("1")));
		assertThat("master.xml should include component_latest.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='component_latest.xml'])", NS, equalTo("1")));
	}

	@Test
	public void testEmptyTag() throws Exception
	{
		final File basedir = resources.getBasedir("testEmptyTag");
		maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "master.xml")));

		assertFilesPresent(basedir, "component_latest.xml");
		assertFilesNotPresent(basedir, "component_1.2.3.xml");
		assertFilesPresent(basedir, "master.xml");

		assertThat("component_latest.xml should not contain a changeSet", xmlFile(basedir, "component_latest.xml"), not(hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS)));

		final Source masterXmlFile = xmlFile(basedir, "master.xml");
		assertThat("master.xml should contain two tagDatabase changeSets", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:changeSet[l:tagDatabase])", NS, equalTo("2")));
		assertThat("master.xml should include component_latest.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='component_latest.xml'])", NS, equalTo("1")));
		assertThat("master.xml should not include component_1.2.3.xml", masterXmlFile, not(hasXPath("/l:databaseChangeLog/l:include[@file='component_1.2.3.xml']", NS)));
	}

	@Test
	public void testEmptyInclude() throws Exception
	{
		final File basedir = resources.getBasedir("testEmptyInclude");
		maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "master.xml")));

		assertFilesPresent(basedir, "component_latest.xml");
		assertFilesNotPresent(basedir, "component_1.2.3.xml");
		assertFilesPresent(basedir, "master.xml");

		assertThat("component_latest.xml should not contain a changeSet", xmlFile(basedir, "component_latest.xml"), not(hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS)));

		final Source masterXmlFile = xmlFile(basedir, "master.xml");
		assertThat("master.xml should contain one tagDatabase changeSet", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:changeSet[l:tagDatabase])", NS, equalTo("1")));
		assertThat("master.xml should include component_latest.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='component_latest.xml'])", NS, equalTo("1")));
		assertThat("master.xml should not include component_1.2.3.xml", masterXmlFile, not(hasXPath("/l:databaseChangeLog/l:include[@file='component_1.2.3.xml']", NS)));
	}

	@Test
	public void testWeirdCharactersInPath() throws Exception
	{
		final File basedir = resources.getBasedir("testWeirdCharactersInPath");
		maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "test%2Ffolder/master.xml")));

		assertFilesPresent(basedir, "test%2Ffolder/test%2Finclude/include%2Ffile_1.2.3.xml", "test%2Ffolder/test%2Finclude/include%2Ffile_latest.xml");
		assertFilesPresent(basedir, "test%2Ffolder/master.xml");

		assertThat("test%2Ffolder/test%2Finclude/include%2Ffile_1.2.3.xml should contain exactly one changeSet", xmlFile(basedir, "test%2Ffolder/test%2Finclude/include%2Ffile_1.2.3.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet)", NS, equalTo("1")));
		assertThat("test%2Ffolder/test%2Finclude/include%2Ffile_latest.xml should not contain a changeSet", xmlFile(basedir, "test%2Ffolder/test%2Finclude/include%2Ffile_latest.xml"), not(hasXPath("/l:databaseChangeLog/l:changeSet", NS)));

		final Source masterXmlFile = xmlFile(basedir, "test%2Ffolder/master.xml");
		assertThat("test%2Ffolder/master.xml should contain one tagDatabase changeSet", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:changeSet[l:tagDatabase])", NS, equalTo("1")));
		assertThat("test%2Ffolder/master.xml should include test%2Finclude/include%2Ffile_1.2.3.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='test%2Finclude/include%2Ffile_1.2.3.xml'])", NS, equalTo("1")));
		assertThat("test%2Ffolder/master.xml should include test%2Finclude/include%2Ffile_latest.xml", masterXmlFile, hasXPath("count(/l:databaseChangeLog/l:include[@file='test%2Finclude/include%2Ffile_latest.xml'])", NS, equalTo("1")));
	}

	@Test
	public void testTagAlreadyExists() throws IOException
	{
		final File basedir = resources.getBasedir("testTagAlreadyExists");
		try
		{
			maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "master.xml")));
		}
		catch (Exception e)
		{
			assertEquals(new VersionAlreadyTaggedException("1.2.3", basedir.toPath().resolve("master.xml")), e);
		}
	}

	@Test
	public void testContexts() throws Exception
	{
		final File basedir = resources.getBasedir("testContexts");
		maven.executeMojo(basedir, "tag", np("newVersion", "1.2.3"), np("masterFiles", np("masterFile", "master.xml")));

		assertThat("master.xml should contain one tagDatabase changeSet for version 1.2.3 without a context", xmlFile(basedir, "master.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet[not(@context) and l:tagDatabase[@tag='1.2.3']])", NS, equalTo("1")));

		maven.executeMojo(basedir, "tag", np("newVersion", "2.0.0"), np("masterFiles", np("masterFile", "master.xml")), np("context", "myContext"));

		assertThat("master.xml should contain one tagDatabase changeSet for version 1.2.3 without a context", xmlFile(basedir, "master.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet[not(@context) and l:tagDatabase[@tag='1.2.3']])", NS, equalTo("1")));
		assertThat("master.xml should contain one tagDatabase changeSet for version 2.0.0 with context 'myContext'", xmlFile(basedir, "master.xml"), hasXPath("count(/l:databaseChangeLog/l:changeSet[@context='myContext' and l:tagDatabase[@tag='2.0.0']])", NS, equalTo("1")));
	}

	@Nonnull
	private static Source xmlFile(@Nonnull final File basedir, @Nonnull final String xmlFile)
	{
		try
		{
			final Path xmlFilePath = basedir.toPath().resolve(xmlFile);
			final List<String> xml = Files.readAllLines(xmlFilePath, Charset.defaultCharset());
			return XmlConverters.the(StringUtils.join(xml.iterator(), ""));
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error reading file " + xmlFile.toString(), e);
		}
	}

	@Nonnull
	private static Xpp3Dom np(@Nonnull final String name, @Nonnull final String value)
	{
		return TestMavenRuntime.newParameter(name, value);
	}

	@Nonnull
	private static Xpp3Dom np(@Nonnull final String name, @Nonnull final Xpp3Dom... value)
	{
		final Xpp3Dom result = new Xpp3Dom(name);
		for (final Xpp3Dom child : value)
		{
			result.addChild(child);
		}
		return result;
	}
}