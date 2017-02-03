# liquibase-db-release-maven-plugin

This is a Maven plugin that is designed to help with tagging a Liquibase file.

## What this plugin does
The plugin will:
- Move all include files to a named variant;
- Create empty latest include files;
- Include a tagDatabase changeSet in the master files

The tagDatabase changeSet looks like:
```xml
<!-- Version 1.2.3 -->
<include file="myApp\1.2.3.xml" relativeToChangelogFile="true"></include>
<changeSet id="Tag 1.2.3" author="liquibase-db-release">
	<tagDatabase tag="1.2.3"></tagDatabase>
</changeSet>
```

## The plugin in more detail
The plugin assumes that the Liquibase has one or more master files. When you create a database, these are the files you run with Liquibase.
The master files are the files where this plugin will insert a tag into.

The plugin also assumes that the master files will include other files. These files are the files that will be versioned.

For example, take the following structure:
- master.xml
- myApp\latest.xml

Master.xml looks like this:
```xml
<databaseChangeLog>
	<changeSet id="Tag our database with 1.0.0" author="John Doe">
		<tagDatabase tag="1.0.0"></tagDatabase>
	</changeSet>
	<include file="myApp\latest.xml" relativeToChangelogFile="true"></include>
</databaseChangeLog>
```

MyApp\latest.xml looks like this:
```xml
<databaseChangeLog>
	<changeSet id="Make a change to our database" author="John Doe">
		...
	</changeSet>
</databaseChangeLog>
```

After running `mvn liquibase-db-release:tag` for version "1.2.3", we have the following structure:
- master.xml
- myApp\1.2.3.xml
- myApp\latest.xml

Master.xml looks like this:
```xml
<databaseChangeLog>
	<changeSet id="Tag our database with 1.0.0" author="John Doe">
		<tagDatabase tag="1.0.0"></tagDatabase>
	</changeSet>

	<!-- Version 1.2.3 -->
	<include file="myApp\1.2.3.xml" relativeToChangelogFile="true"></include>
	<changeSet id="Tag 1.2.3" author="liquibase-db-release">
		<tagDatabase tag="1.2.3"></tagDatabase>
	</changeSet>

	<include file="myApp\latest.xml" relativeToChangelogFile="true"></include>
</databaseChangeLog>
```

MyApp\1.2.3.xml looks like this:
```xml
<databaseChangeLog>
	<changeSet id="Make a change to our database" author="John Doe">
		...
	</changeSet>
</databaseChangeLog>
```

MyApp\latest.xml looks like this:
```xml
<databaseChangeLog>
</databaseChangeLog>
```


## How to use this plugin
### Pom.xml
Configure this plugin in your pom.xml as follows:
```
<plugin>
	<groupId>org.jurr.liquibase</groupId>
	<artifactId>liquibase-db-release-maven-plugin</artifactId>
	<version>${liquibase-db-release.version}</version>
	<configuration>
		<masterFiles>
			<masterFile>liquibase/master.xml</masterFile>
			<masterFile>liquibase/master_alternative.xml</masterFile>
		</masterFiles>
		<skippedIncludeFiles>
			<skippedIncludeFile>liquibase/demo_content.xml</skippedIncludeFile>
			<skippedIncludeFile>liquibase/test_content.xml</skippedIncludeFile>
		</skippedIncludeFiles>
	</configuration>
</plugin>
```

Just run `mvn liquibase-db-release:tag`. The plugin will ask you for a new version.
If you want to run this in batch mode, use `mvn liquibase-db-release:tag -B -DnewVersion=1.2.3`.
