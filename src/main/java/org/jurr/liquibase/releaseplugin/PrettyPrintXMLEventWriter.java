package org.jurr.liquibase.releaseplugin;

import javax.annotation.Nonnull;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.XMLEvent;

/**
 * Simple XMLEventWriter that does indenting and removes duplicate whitelines.
 * It wraps another XMLEventWriter
 */
public class PrettyPrintXMLEventWriter implements XMLEventWriter
{
	private static final XMLEventFactory XML_EVENT_FACTORY = XMLEventFactory.newInstance();

	private final XMLEventWriter wrappedWriter;
	private final String indentPrefix;

	private int indent = 0;
	private boolean lastEventWasNewline = false;
	private boolean forLastEventWasNewline = false;

	public PrettyPrintXMLEventWriter(@Nonnull final XMLEventWriter wrappedWriter, @Nonnull final String indentPrefix)
	{
		this.wrappedWriter = wrappedWriter;
		this.indentPrefix = indentPrefix;
	}

	@Nonnull
	public String getIndentPrefix()
	{
		return indentPrefix;
	}

	@Override
	public void flush() throws XMLStreamException
	{
		wrappedWriter.flush();
	}

	@Override
	public void close() throws XMLStreamException
	{
		wrappedWriter.close();
	}

	@Override
	public void add(final XMLEvent event) throws XMLStreamException
	{
		if (event.isEndElement())
		{
			indent--;
		}

		if (event.isCharacters())
		{
			// Only add one empty line (== 2 sequential newline characters), don't add other characters
			final Characters characters = (Characters) event;
			for (int i = 0; i < characters.getData().length(); i++)
			{
				char c = characters.getData().charAt(i);
				if (c == '\n')
				{
					if (!lastEventWasNewline)
					{
						wrappedWriter.add(XML_EVENT_FACTORY.createCharacters("\n"));
						lastEventWasNewline = true;
						forLastEventWasNewline = false;
					}
					else if (!forLastEventWasNewline)
					{
						wrappedWriter.add(XML_EVENT_FACTORY.createCharacters("\n"));
						forLastEventWasNewline = true;
					}
				}
			}
		}
		else
		{
			if (lastEventWasNewline)
			{
				for (int i = 0; i < indent; i++)
				{
					wrappedWriter.add(XML_EVENT_FACTORY.createCharacters(indentPrefix));
				}
			}

			wrappedWriter.add(event);
			lastEventWasNewline = false;
		}

		if (event.isStartElement())
		{
			indent++;
		}

		if (event.isStartDocument())
		{
			add(XML_EVENT_FACTORY.createCharacters("\n"));
		}
	}

	@Override
	public void add(XMLEventReader reader) throws XMLStreamException
	{
		wrappedWriter.add(reader);
	}

	@Override
	public String getPrefix(String uri) throws XMLStreamException
	{
		return wrappedWriter.getPrefix(uri);
	}

	@Override
	public void setPrefix(String prefix, String uri) throws XMLStreamException
	{
		wrappedWriter.setPrefix(prefix, uri);
	}

	@Override
	public void setDefaultNamespace(String uri) throws XMLStreamException
	{
		wrappedWriter.setDefaultNamespace(uri);
	}

	@Override
	public void setNamespaceContext(NamespaceContext context) throws XMLStreamException
	{
		wrappedWriter.setNamespaceContext(context);
	}

	@Override
	public NamespaceContext getNamespaceContext()
	{
		return wrappedWriter.getNamespaceContext();
	}
}