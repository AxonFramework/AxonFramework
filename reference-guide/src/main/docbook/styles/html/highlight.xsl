<?xml version='1.0'?>
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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xslthl="http://xslthl.sf.net"
                exclude-result-prefixes="xslthl"
                version='1.0'>

    <!-- ********************************************************************
   $Id: highlight.xsl 8419 2009-04-29 20:37:52Z kosek $
   ********************************************************************

   This file is part of the XSL DocBook Stylesheet distribution.
   See ../README or http://docbook.sf.net/release/xsl/current/ for
   and other information.

   ******************************************************************** -->

    <xsl:import href="../highlighting/common.xsl"/>

    <xsl:param name="highlight.source" select="1"/>
	<xsl:param name="highlight.default.language" select="java"/>

    <xsl:template match='xslthl:tag' mode="xslthl">
        <span style="color: #000080">
            <xsl:apply-templates/>
        </span>
    </xsl:template>

    <xsl:template match='xslthl:attribute' mode="xslthl">
        <strong>
            <xsl:apply-templates/>
        </strong>
    </xsl:template>

    <xsl:template match='xslthl:value' mode="xslthl">
        <inline font-weight="bold" color="#008000">
            <xsl:apply-templates/>
        </inline>
    </xsl:template>

    <xsl:template match='xslthl:keyword' mode="xslthl">
        <span style="color: #000080">
            <xsl:apply-templates mode="xslthl"/>
        </span>
    </xsl:template>

    <xsl:template match='xslthl:string' mode="xslthl">
        <inline font-weight="bold" font-style="italic">
            <xsl:apply-templates mode="xslthl"/>
        </inline>
    </xsl:template>

    <xsl:template match='xslthl:comment' mode="xslthl">
        <inline font-style="italic">
            <xsl:apply-templates mode="xslthl"/>
        </inline>
    </xsl:template>

    <!--
    <xsl:template match='xslthl:html'>
      <span style='background:#AFF'><font color='blue'><xsl:apply-templates/></font></span>
    </xsl:template>

    <xsl:template match='xslthl:xslt'>
      <span style='background:#AAA'><font color='blue'><xsl:apply-templates/></font></span>
    </xsl:template>

    <xsl:template match='xslthl:section'>
      <span style='background:yellow'><xsl:apply-templates/></span>
    </xsl:template>
    -->

    <xsl:template match='xslthl:number' mode="xslthl">
        <xsl:apply-templates mode="xslthl"/>
    </xsl:template>

    <xsl:template match='xslthl:annotation' mode="xslthl">
        <inline color="#808000">
            <xsl:apply-templates mode="xslthl"/>
        </inline>
    </xsl:template>

    <xsl:template match='xslthl:directive' mode="xslthl">
        <xsl:apply-templates mode="xslthl"/>
    </xsl:template>

    <!-- Not sure which element will be in final XSLTHL 2.0 -->
    <xsl:template match='xslthl:doccomment|xslthl:doctype' mode="xslthl">
        <inline font-weight="bold">
            <xsl:apply-templates mode="xslthl"/>
        </inline>
    </xsl:template>


</xsl:stylesheet>

