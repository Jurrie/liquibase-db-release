package org.jurr.liquibase.releaseplugin.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.plexus.util.StringUtils;
import org.jurr.liquibase.releaseplugin.Utils;
import org.jurr.liquibase.releaseplugin.exceptions.VersionAlreadyTaggedException;

public class MasterFile extends DatabaseChangeLogFile
{
	private final List<IncludeFile> includedFiles = new ArrayList<>();

	private boolean bufferingXMLEvents = false;
	private List<XMLEvent> bufferedXMLEvents = new LinkedList<>();

	public MasterFile(@Nonnull final Path path)
	{
		super(path);
	}

	private void addXMLEventToOutput(@Nonnull final XMLEventWriter xmlEventWriter, @Nonnull final XMLEvent xmlEvent) throws XMLStreamException
	{
		if (bufferingXMLEvents)
		{
			bufferedXMLEvents.add(xmlEvent);
		}
		else
		{
			xmlEventWriter.add(xmlEvent);
		}
	}

	private void flushBufferedXMLEvents(@Nonnull final XMLEventWriter xmlEventWriter) throws XMLStreamException
	{
		if (bufferingXMLEvents)
		{
			for (XMLEvent xmlEvent : bufferedXMLEvents)
			{
				xmlEventWriter.add(xmlEvent);
			}
			bufferedXMLEvents.clear();
			bufferingXMLEvents = false;
		}
	}

	@Override
	void readStartDocumentTag(@Nonnull final XMLEventReader xmlEventReader, @Nonnull final StartDocument startDocumentElement, @Nonnull final XMLEventWriter xmlEventWriter) throws XMLStreamException
	{
		xmlEventWriter.add(startDocumentElement);

		while (xmlEventReader.hasNext())
		{
			final XMLEvent xmlEvent = xmlEventReader.nextEvent();

			switch (xmlEvent.getEventType())
			{
			case XMLStreamConstants.START_ELEMENT:
				final StartElement xmlStartElement = (StartElement) xmlEvent;
				if (xmlStartElement.getName().equals(DATABASE_CHANGELOG_TAG))
				{
					// Skip
					addXMLEventToOutput(xmlEventWriter, xmlEvent);
				}
				else if (xmlStartElement.getName().equals(INCLUDE_TAG))
				{
					// This is possibly a file to include
					readIncludeTag(xmlStartElement);

					bufferingXMLEvents = true;
					addXMLEventToOutput(xmlEventWriter, xmlEvent);
				}
				else if (xmlStartElement.getName().equals(CHANGE_SET_TAG))
				{
					// This is possibly a <tagDatabase /> changeSet
					addXMLEventToOutput(xmlEventWriter, xmlEvent);

					final String taggedVersion = readChangeSetTag(xmlEventReader, xmlEventWriter);
					if (taggedVersion == null)
					{
						// changeSet tag does not contain a tagDatabase tag; skip it.
					}
					else if (taggedVersion.equals(getLiquibaseProject().getNewVersion()))
					{
						throw new VersionAlreadyTaggedException(getLiquibaseProject().getNewVersion(), getPath());
					}
					else
					{
						// We have found a tagDatabase tag. All stuff before this tag is already tagged, so start fresh again.
						includedFiles.clear();

						flushBufferedXMLEvents(xmlEventWriter);
					}
				}
				else
				{
					throw new IllegalArgumentException("Unknown tag in file " + getPath() + " at line " + xmlEvent.getLocation().getLineNumber() + ", column " + xmlEvent.getLocation().getColumnNumber());
				}
				break;
			case XMLStreamConstants.END_ELEMENT:
				final EndElement xmlEndElement = (EndElement) xmlEvent;
				if (xmlEndElement.getName().equals(DATABASE_CHANGELOG_TAG))
				{
					bufferingXMLEvents = true;
				}
				addXMLEventToOutput(xmlEventWriter, xmlEvent);
				break;
			default:
				addXMLEventToOutput(xmlEventWriter, xmlEvent);
				break;
			}
		}

		for (int i = 0; i < includedFiles.size(); i++)
		{
			final IncludeFile includeFile = includedFiles.get(i);
			final IncludeFile actualIncludeFile = getLiquibaseProject().addIncludeFile(includeFile);
			includedFiles.set(i, actualIncludeFile);
		}

		insertNewVersionIncludesAndTag(xmlEventWriter);

		flushBufferedXMLEvents(xmlEventWriter);
	}

	private void readIncludeTag(@Nonnull final StartElement includeStartElement)
	{
		final Path includeFilePath = Paths.get(includeStartElement.getAttributeByName(INCLUDE_TAG_FILE_ATTRIBUTE).getValue());

		final String relativeToChangelogFileString = includeStartElement.getAttributeByName(INCLUDE_TAG_RELATIVE_TO_CHANGE_LOG_FILE_ATTRIBUTE).getValue();
		final boolean relativeToChangelogFile = Boolean.valueOf(relativeToChangelogFileString);

		final IncludeFile includeFile = new IncludeFile(includeFilePath, this, relativeToChangelogFile, getLiquibaseProject().getClasspathRoot(), includeStartElement.getAttributes());
		includedFiles.add(includeFile);
	}

	@CheckForNull
	private String readChangeSetTag(@Nonnull final XMLEventReader xmlEventReader, @Nonnull final XMLEventWriter xmlEventWriter) throws XMLStreamException
	{
		String lastFoundTag = null;

		while (xmlEventReader.hasNext())
		{
			final XMLEvent xmlEvent = xmlEventReader.nextEvent();
			addXMLEventToOutput(xmlEventWriter, xmlEvent);

			switch (xmlEvent.getEventType())
			{
			case XMLStreamConstants.START_ELEMENT:
				final StartElement xmlStartElement = (StartElement) xmlEvent;
				if (xmlStartElement.getName().equals(TAG_DATABASE_TAG))
				{
					lastFoundTag = xmlStartElement.getAttributeByName(TAG_DATABASE_TAG_ATTRIBUTE).getValue();
				}
				break;
			case XMLStreamConstants.END_ELEMENT:
				final EndElement xmlEndElement = (EndElement) xmlEvent;
				if (xmlEndElement.getName().equals(CHANGE_SET_TAG))
				{
					return lastFoundTag;
				}
				break;
			}
		}
		throw new IllegalArgumentException("No end tag found for <changeSet> tag");
	}

	private void insertNewVersionIncludesAndTag(@Nonnull final XMLEventWriter xmlEventWriter) throws XMLStreamException, FactoryConfigurationError
	{
		final String newVersion = getLiquibaseProject().getNewVersion();

		xmlEventWriter.add(XML_EVENT_FACTORY.createCharacters("\n\n"));

		xmlEventWriter.add(XML_EVENT_FACTORY.createComment(" Version " + newVersion + " "));
		xmlEventWriter.add(XML_EVENT_FACTORY.createCharacters("\n"));

		for (IncludeFile includeFile : includedFiles)
		{
			if (includeFile.willBeTagged())
			{
				final String newVersionFilename = Utils.convertPathSeparatorToForwardSlash(includeFile.getNewVersionFilename(true));
				final List<Attribute> attributesForIncludeTag = getAttributesForIncludeTag(includeFile.getAttributes(), newVersionFilename);

				xmlEventWriter.add(XML_EVENT_FACTORY.createStartElement(INCLUDE_TAG, attributesForIncludeTag.iterator(), null));
				xmlEventWriter.add(XML_EVENT_FACTORY.createEndElement(INCLUDE_TAG, null));
				xmlEventWriter.add(XML_EVENT_FACTORY.createCharacters("\n"));
			}
		}

		final List<Attribute> attributes = new ArrayList<>(3);
		attributes.add(XML_EVENT_FACTORY.createAttribute(CHANGE_SET_TAG_ID_ATTRIBUTE, "Tag " + newVersion));
		attributes.add(XML_EVENT_FACTORY.createAttribute(CHANGE_SET_TAG_AUTHOR_ATTRIBUTE, "liquibase-db-release"));
		if (StringUtils.isNotBlank(getLiquibaseProject().getContext()))
		{
			attributes.add(XML_EVENT_FACTORY.createAttribute(CHANGE_SET_TAG_CONTEXT_ATTRIBUTE, getLiquibaseProject().getContext()));
		}

		xmlEventWriter.add(XML_EVENT_FACTORY.createStartElement(CHANGE_SET_TAG, attributes.iterator(), null));
		xmlEventWriter.add(XML_EVENT_FACTORY.createCharacters("\n"));

		final Attribute tag = XML_EVENT_FACTORY.createAttribute(TAG_DATABASE_TAG_ATTRIBUTE, newVersion);
		xmlEventWriter.add(XML_EVENT_FACTORY.createStartElement(TAG_DATABASE_TAG, Arrays.asList(tag).iterator(), null));
		xmlEventWriter.add(XML_EVENT_FACTORY.createEndElement(TAG_DATABASE_TAG, null));
		xmlEventWriter.add(XML_EVENT_FACTORY.createCharacters("\n"));

		xmlEventWriter.add(XML_EVENT_FACTORY.createEndElement(CHANGE_SET_TAG, null));
		xmlEventWriter.add(XML_EVENT_FACTORY.createCharacters("\n\n"));
	}

	@Nonnull
	private List<Attribute> getAttributesForIncludeTag(@Nonnull final List<Attribute> originalAtributesOnIncludeTag, @Nonnull final String newVersionFilename)
	{
		final List<Attribute> result = new ArrayList<>(originalAtributesOnIncludeTag.size());
		result.add(XML_EVENT_FACTORY.createAttribute(INCLUDE_TAG_FILE_ATTRIBUTE, newVersionFilename));
		result.add(XML_EVENT_FACTORY.createAttribute(INCLUDE_TAG_RELATIVE_TO_CHANGE_LOG_FILE_ATTRIBUTE, "true"));

		// Now copy all the other attributes
		for (Attribute originalAttribute : originalAtributesOnIncludeTag)
		{
			if (!originalAttribute.getName().equals(INCLUDE_TAG_FILE_ATTRIBUTE) && !originalAttribute.getName().equals(INCLUDE_TAG_RELATIVE_TO_CHANGE_LOG_FILE_ATTRIBUTE))
			{
				result.add(originalAttribute);
			}
		}

		return result;
	}

	@Override
	void createNewVersion() throws IOException
	{
		readFile();

		Files.write(getPath(), getNewLatestFile());
	}
}