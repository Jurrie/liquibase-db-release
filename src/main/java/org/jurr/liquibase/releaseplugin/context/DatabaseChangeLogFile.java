package org.jurr.liquibase.releaseplugin.context;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.XMLEvent;

import org.jurr.liquibase.releaseplugin.PrettyPrintXMLEventWriter;

public abstract class DatabaseChangeLogFile
{
	public static final String LIQUIBASE_NAMESPACE = "http://www.liquibase.org/xml/ns/dbchangelog";

	protected static final XMLEventFactory XML_EVENT_FACTORY = XMLEventFactory.newInstance();

	private static final XMLInputFactory XML_INPUT_FACTORY;
	private static final XMLOutputFactory XML_OUTPUT_FACTORY;
	static
	{
		XML_INPUT_FACTORY = XMLInputFactory.newInstance();
		XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);

		XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();
		XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
	}

	public static final QName DATABASE_CHANGELOG_TAG = new QName(LIQUIBASE_NAMESPACE, "databaseChangeLog");
	public static final QName CHANGE_SET_TAG = new QName(LIQUIBASE_NAMESPACE, "changeSet");
	public static final QName CHANGE_SET_TAG_ID_ATTRIBUTE = new QName("", "id");
	public static final QName CHANGE_SET_TAG_AUTHOR_ATTRIBUTE = new QName("", "author");
	public static final QName CHANGE_SET_TAG_CONTEXT_ATTRIBUTE = new QName("", "context");
	public static final QName INCLUDE_TAG = new QName(LIQUIBASE_NAMESPACE, "include");
	public static final QName INCLUDE_TAG_FILE_ATTRIBUTE = new QName("", "file");
	public static final QName INCLUDE_TAG_RELATIVE_TO_CHANGE_LOG_FILE_ATTRIBUTE = new QName("", "relativeToChangelogFile");
	public static final QName TAG_DATABASE_TAG = new QName(LIQUIBASE_NAMESPACE, "tagDatabase");
	public static final QName TAG_DATABASE_TAG_ATTRIBUTE = new QName("", "tag");

	private final Path path;

	private boolean fileRead = false;
	private LiquibaseProject liquibaseProject;
	private byte[] newLatestFile;

	DatabaseChangeLogFile(@Nonnull final Path path)
	{
		if (Files.notExists(path))
		{
			throw new IllegalArgumentException("File " + path + " does not exist");
		}
		if (!Files.isReadable(path))
		{
			throw new IllegalArgumentException("File " + path + " is not readable");
		}
		this.path = path;
	}

	@Nonnull
	LiquibaseProject getLiquibaseProject()
	{
		return liquibaseProject;
	}

	void setLiquibaseProject(@Nonnull final LiquibaseProject liquibaseProject)
	{
		this.liquibaseProject = liquibaseProject;
	}

	@Nonnull
	Path getPath()
	{
		return path;
	}

	@Nonnull
	byte[] getNewLatestFile()
	{
		return newLatestFile;
	}

	void readFile()
	{
		if (fileRead)
		{
			return;
		}

		try (BufferedReader masterFileBR = Files.newBufferedReader(path, Charset.defaultCharset()))
		{
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
			{
				final XMLEventReader xmlEventReader = XML_INPUT_FACTORY.createXMLEventReader(masterFileBR);
				final XMLEventWriter xmlEventWriter = new PrettyPrintXMLEventWriter(XML_OUTPUT_FACTORY.createXMLEventWriter(baos), "\t");
				while (xmlEventReader.hasNext())
				{
					final XMLEvent xmlEvent = xmlEventReader.nextEvent();
					switch (xmlEvent.getEventType())
					{
					case XMLStreamConstants.START_DOCUMENT:
						final StartDocument xmlStartDocument = (StartDocument) xmlEvent;
						readStartDocumentTag(xmlEventReader, xmlStartDocument, xmlEventWriter);
						break;
					default:
						throw new IllegalArgumentException("Unknown tag in file " + path + " at line " + xmlEvent.getLocation().getLineNumber() + ", column " + xmlEvent.getLocation().getColumnNumber());
					}
				}

				xmlEventWriter.flush();
				newLatestFile = baos.toByteArray();
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("IO error while reading " + path, e);
		}
		catch (XMLStreamException e)
		{
			throw new RuntimeException("XML exception while reading " + path, e);
		}

		fileRead = true;
	}

	abstract void readStartDocumentTag(@Nonnull final XMLEventReader xmlEventReader, @Nonnull final StartDocument startDocumentElement, @Nonnull final XMLEventWriter xmlEventWriter) throws XMLStreamException;

	/**
	 * This method will actually create the required files for the new database version.
	 *
	 * @throws IOException whenever there is an exception while creating or moving files
	 */
	abstract void createNewVersion() throws IOException;

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + (path == null ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(@Nullable final Object obj)
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
		DatabaseChangeLogFile other = (IncludeFile) obj;
		if (path == null)
		{
			if (other.path != null)
			{
				return false;
			}
		}
		else if (!path.equals(other.path))
		{
			return false;
		}
		return true;
	}
}