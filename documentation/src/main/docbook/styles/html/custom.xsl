<?xml version="1.0" encoding="UTF-8"?>

<!--
  ~ Copyright (c) 2010-2012. Axon Framework
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xslthl="http://xslthl.sf.net"
                exclude-result-prefixes="xslthl"
                version='1.0'>


    <xsl:import href="coverpages.xsl"/>
    <xsl:import href="highlight.xsl"/>
    <xsl:param name="admon.graphics.path">./</xsl:param>

    <xsl:param name="chunker.output.method">html</xsl:param>
    <xsl:param name="root.filename">index</xsl:param>
    <xsl:param name="img.src.path">images/</xsl:param>

    <xsl:param name="highlight.xslthl.config.path"/>
    <xsl:variable name="highlight.xslthl.config" select="$highlight.xslthl.config.path"/>

	<xsl:param name="chunk.section.depth">'5'</xsl:param>
	<xsl:param name="use.id.as.filename" select="1"/>

    <!-- Extensions -->
    <xsl:param name="use.extensions">1</xsl:param>
	<xsl:param name="tablecolumns.extension">0</xsl:param>
	<xsl:param name="callout.extensions">1</xsl:param>

    <!-- Activate Graphics -->
    <xsl:param name="admon.graphics" select="1"/>
    <!--<xsl:param name="admon.graphics.path">images/</xsl:param>-->
    <xsl:param name="admon.graphics.extension">.gif</xsl:param>
	<xsl:param name="callout.graphics" select="1"/>
	<xsl:param name="callout.defaultcolumn">120</xsl:param>
    <!--<xsl:param name="callout.graphics.path">images/callouts/</xsl:param>-->
    <xsl:param name="callout.graphics.path">callouts/</xsl:param>
	<xsl:param name="callout.graphics.extension">.gif</xsl:param>

	<xsl:param name="table.borders.with.css" select="1"/>
	<xsl:param name="html.stylesheet">css/stylesheet.css</xsl:param>
	<xsl:param name="html.stylesheet.type">text/css</xsl:param>
	<xsl:param name="generate.toc">book toc,title</xsl:param>

	<xsl:param name="admonition.title.properties">text-align: left</xsl:param>

    <!-- Label Chapters and Sections (numbering) -->
    <xsl:param name="chapter.autolabel" select="1"/>
	<xsl:param name="section.autolabel" select="1"/>
	<xsl:param name="section.autolabel.max.depth" select="3"/>

	<xsl:param name="section.label.includes.component.label" select="1"/>
	<xsl:param name="table.footnote.number.format" select="'1'"/>

    <!-- Show only Sections up to level 3 in the TOCs -->
    <xsl:param name="toc.section.depth">3</xsl:param>

    <!-- Remove "Chapter" from the Chapter titles... -->
    <xsl:param name="local.l10n.xml" select="document('')"/>
	<l:i18n xmlns:l="http://docbook.sourceforge.net/xmlns/l10n/1.0">
		<l:l10n language="en">
			<l:context name="title-numbered">
				<l:template name="chapter" text="%n.&#160;%t"/>
				<l:template name="section" text="%n&#160;%t"/>
			</l:context>
		</l:l10n>
	</l:i18n>

    <!-- Google Analytics -->


</xsl:stylesheet>