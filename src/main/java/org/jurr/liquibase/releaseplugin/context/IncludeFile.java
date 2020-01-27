package org.jurr.liquibase.releaseplugin.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.jurr.liquibase.releaseplugin.Utils;

public class IncludeFile extends DatabaseChangeLogFile
{
	private static final String PART_OF_FILENAME_THAT_WILL_BE_REPLACED_WITH_VERSION = "latest";

	private final MasterFile masterFile;
	private final List<Attribute> attributes;
	private boolean containsChangeSets;

	IncludeFile(@Nonnull final Path path, @Nonnull final MasterFile masterFile, final boolean relativeToChangelogFile, @Nonnull final Path classpathRoot, @Nonnull final Iterator<Attribute> attributeIterator)
	{
		super(Utils.resolveIncludeFile(masterFile.getPath(), path, relativeToChangelogFile, classpathRoot));
		attributes = iteratorToList(attributeIterator);
		this.masterFile = masterFile;
	}

	@Nonnull
	private <T> List<T> iteratorToList(@Nonnull final Iterator<T> iterator)
	{
		final List<T> result = new ArrayList<>();
		while (iterator.hasNext())
		{
			result.add(iterator.next());
		}
		return result;
	}

	public boolean willBeTagged()
	{
		final boolean shouldBeSkipped = masterFile.getLiquibaseProject().getIncludeFilesToSkipTagging().contains(getPath());
		return !shouldBeSkipped && containsChangeSets();
	}

	@Nonnull
	public List<Attribute> getAttributes()
	{
		return attributes;
	}

	private boolean containsChangeSets()
	{
		readFile();
		return containsChangeSets;
	}

	@Nonnull
	Path getNewVersionFilename(final boolean relativeToChangelogFile)
	{
		final Path newVersionFilename = Utils.replaceStringInPath(getPath(), PART_OF_FILENAME_THAT_WILL_BE_REPLACED_WITH_VERSION, getLiquibaseProject().getNewVersion());
		if (relativeToChangelogFile)
		{
			return masterFile.getPath().getParent().relativize(newVersionFilename);
		}
		else
		{
			return newVersionFilename;
		}
	}

	@Override
	void readStartDocumentTag(@Nonnull final XMLEventReader xmlEventReader, @Nonnull final StartDocument startDocument, @Nonnull final XMLEventWriter xmlEventWriter) throws XMLStreamException
	{
		xmlEventWriter.add(startDocument);

		while (xmlEventReader.hasNext())
		{
			final XMLEvent xmlEvent = xmlEventReader.nextEvent();
			switch (xmlEvent.getEventType())
			{
			case XMLStreamConstants.START_ELEMENT:
				final StartElement xmlStartElement = (StartElement) xmlEvent;
				if (xmlStartElement.getName().equals(CHANGE_SET_TAG))
				{
					containsChangeSets = true;
					Utils.skipUntillEndElement(xmlEventReader, xmlStartElement);
				}
				else
				{
					xmlEventWriter.add(xmlEvent);
				}
				break;
			default:
				xmlEventWriter.add(xmlEvent);
				break;
			}
		}
	}

	@Override
	void createNewVersion() throws IOException
	{
		readFile();

		if (!willBeTagged())
		{
			return;
		}

		// Move this file to the version variant.
		Path newFile = getNewVersionFilename(false);
		Files.createDirectories(newFile.getParent());
		Files.move(getPath(), newFile);

		// Create an empty copy with the original filename.
		Files.write(getPath(), getNewLatestFile(), StandardOpenOption.CREATE_NEW);
	}
}