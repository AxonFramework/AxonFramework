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

<xsl:stylesheet version='1.0'
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                exclude-result-prefixes="#default">

    <xsl:include href="basic.xsl"/>

    <!--###################################################-->
    <!--##               The cover page                  ##-->
    <!--###################################################-->

    <xsl:template name="book.titlepage.recto">
        <xsl:choose>
            <xsl:when test="bookinfo/graphic">
                <xsl:apply-templates select="bookinfo/graphic"/>
            </xsl:when>
            <xsl:when test="info/graphic">
                <xsl:apply-templates select="info/graphic"/>
            </xsl:when>
            <xsl:when test="bookinfo/mediaobject">
                <xsl:apply-templates select="bookinfo/mediaobject"/>
            </xsl:when>
            <xsl:when test="info/mediaobject">
                <xsl:apply-templates select="info/mediaobject"/>
            </xsl:when>
        </xsl:choose>

        <xsl:choose>
            <xsl:when test="bookinfo/title">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/title"/>
            </xsl:when>
            <xsl:when test="info/title">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="info/title"/>
            </xsl:when>
            <xsl:when test="title">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="title"/>
            </xsl:when>
        </xsl:choose>

        <xsl:choose>
            <xsl:when test="bookinfo/subtitle">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/subtitle"/>
            </xsl:when>
            <xsl:when test="info/subtitle">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="info/subtitle"/>
            </xsl:when>
            <xsl:when test="subtitle">
                <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="subtitle"/>
            </xsl:when>
        </xsl:choose>

        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/releaseinfo"/>
        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="info/releaseinfo"/>
        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="releaseinfo"/>

        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/authorgroup/author"/>
        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="info/authorgroup/author"/>
        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="bookinfo/author"/>
        <xsl:apply-templates mode="book.titlepage.recto.auto.mode" select="info/author"/>
    </xsl:template>

    <xsl:template match="title" mode="book.titlepage.recto.auto.mode">
        <fo:block xmlns:fo="http://www.w3.org/1999/XSL/Format"
                  xsl:use-attribute-sets="book.titlepage.recto.style"
                  text-align="center"
                  font-size="24pt"
                  space-before="18.6624pt"
                  font-weight="bold"
                  font-family="{$title.fontset}">
            <xsl:call-template name="division.title">
                <xsl:with-param name="node" select="ancestor-or-self::book[1]"/>
            </xsl:call-template>
        </fo:block>
    </xsl:template>

    <xsl:template match="subtitle" mode="book.titlepage.recto.auto.mode">
        <fo:block xmlns:fo="http://www.w3.org/1999/XSL/Format"
                  xsl:use-attribute-sets="book.titlepage.recto.style"
                  text-align="center"
                  font-size="16pt"
                  space-before="30pt"
                  font-weight="bold"
                  font-style="italic"
                  font-family="{$title.fontset}">
            <xsl:apply-templates select="." mode="book.titlepage.recto.mode"/>
        </fo:block>
    </xsl:template>

    <xsl:template match="releaseinfo" mode="book.titlepage.recto.auto.mode">
        <fo:block xmlns:fo="http://www.w3.org/1999/XSL/Format"
                  xsl:use-attribute-sets="book.titlepage.recto.style"
                  text-align="center"
                  font-size="16pt"
                  space-before="15.552pt"
                  font-family="{$title.fontset}">
            <xsl:apply-templates select="." mode="book.titlepage.recto.mode"/>
        </fo:block>
    </xsl:template>

    <xsl:template match="author" mode="book.titlepage.recto.auto.mode">
        <fo:block xsl:use-attribute-sets="book.titlepage.recto.style" font-size="14pt" space-before="15.552pt">
            <xsl:call-template name="person.name">
                <xsl:with-param name="node" select="."/>
            </xsl:call-template>
        </fo:block>
    </xsl:template>

    <!--###################################################-->
    <!--##           The secondary cover page            ##-->
    <!--###################################################-->
    <xsl:template name="book.titlepage.verso">
        <!-- empty -->
    </xsl:template>

    <xsl:template name="book.verso.title">
        <fo:block>
            <xsl:apply-templates mode="titlepage.mode"/>
        </fo:block>
    </xsl:template>

    <xsl:template name="verso.authorgroup">
        <fo:table table-layout="fixed" width="100%">
            <fo:table-column column-number="1" column-width="proportional-column-width(1)"/>
            <fo:table-column column-number="2" column-width="proportional-column-width(1)"/>
            <fo:table-column column-number="3" column-width="proportional-column-width(1)"/>
            <fo:table-body>
                <xsl:apply-templates select="author" mode="tablerow.titlepage.mode"/>
                <xsl:apply-templates select="editor" mode="tablerow.titlepage.mode"/>
                <xsl:apply-templates select="othercredit" mode="tablerow.titlepage.mode"/>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <xsl:template name="book.titlepage.separator">
    </xsl:template>

    <xsl:template name="book.titlepage.before.recto">
    </xsl:template>

    <xsl:template name="book.titlepage.before.verso">
        <fo:block xmlns:fo="http://www.w3.org/1999/XSL/Format" break-after="page"/>
    </xsl:template>

    <xsl:template match="author" mode="tablerow.titlepage.mode">
        <fo:table-row>
            <fo:table-cell>
                <fo:block>
                    <xsl:call-template name="gentext">
                        <xsl:with-param name="key" select="'Author'"/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
            <fo:table-cell>
                <fo:block>
                    <xsl:call-template name="person.name">
                        <xsl:with-param name="node" select="."/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
            <fo:table-cell>
                <fo:block>
                    <xsl:apply-templates select="email"/>
                </fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>

    <xsl:template match="author" mode="titlepage.mode">
        <fo:block>
            <xsl:call-template name="person.name">
                <xsl:with-param name="node" select="."/>
            </xsl:call-template>
        </fo:block>
    </xsl:template>

    <xsl:param name="editedby.enabled">0</xsl:param>

    <xsl:template match="editor" mode="tablerow.titlepage.mode">
        <fo:table-row>
            <fo:table-cell>
                <fo:block>
                    <xsl:call-template name="gentext">
                        <xsl:with-param name="key" select="'Editor'"/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
            <fo:table-cell>
                <fo:block>
                    <xsl:call-template name="person.name">
                        <xsl:with-param name="node" select="."/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
            <fo:table-cell>
                <fo:block>
                    <xsl:apply-templates select="email"/>
                </fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>

    <xsl:template match="othercredit" mode="tablerow.titlepage.mode">
        <fo:table-row>
            <fo:table-cell>
                <fo:block>
                    <xsl:call-template name="gentext">
                        <xsl:with-param name="key" select="'translator'"/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
            <fo:table-cell>
                <fo:block>
                    <xsl:call-template name="person.name">
                        <xsl:with-param name="node" select="."/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
            <fo:table-cell>
                <fo:block>
                    <xsl:apply-templates select="email"/>
                </fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>


</xsl:stylesheet>