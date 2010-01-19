<?xml version="1.0" encoding="utf-8"?>
<!--
  ~ Copyright (c) 2010. Axon Framework
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

<!--
    This is the XSL HTML configuration file for the Spring
    Reference Documentation.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                version="1.0">

    <xsl:import href="http://docbook.sourceforge.net/release/xsl/current/html/chunk.xsl"/>
    <xsl:import href="basic.xsl"/>
    <xsl:import href="coverpages_html.xsl"/>
    <xsl:import href="highlight_html.xsl"/>

    <xsl:param name="chunker.output.method">html</xsl:param>
    <xsl:param name="root.filename">index</xsl:param>

    <xsl:param name="highlight.xslthl.config.path"/>
    <xsl:variable name="highlight.xslthl.config" select="$highlight.xslthl.config.path"/>
</xsl:stylesheet>
