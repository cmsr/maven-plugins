package com.github.goldin.plugins.jenkins.markup

import com.github.goldin.plugins.jenkins.Job
import org.gcontracts.annotations.Requires


/**
 * Generates Jenkins config file XML markup.
 */
class ConfigMarkup extends Markup
{
    private final Job    job
    private final String timestamp

    @Requires({ job && ( timestamp != null ) })
    ConfigMarkup ( Job job, String timestamp )
    {
        this.job       = job
        this.timestamp = timestamp
    }


    /**
     * Builds Jenkins config XML markup using this object markup builder.
     */
    @Override
    void buildMarkup ()
    {
        builder.with {
            mkp.with {
                xmlDeclaration( version: '1.0', encoding: 'UTF-8' )
                add( '<!-- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ -->\n' )
                add( "<!-- Generated automatically by [${ job.generationPom }]${ timestamp } -->\n" )
                add( '<!-- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ -->\n' )
            }
            "${ Job.JobType.maven.is( job.jobType ) ? 'maven2-moduleset' : 'project' }" {
                actions()
                addDescription()
            }
        }
    }


    /**
     * Adds config's {@code <description>} section.
     */
    void addDescription ()
    {
        builder.description {
            add( """
<![CDATA[<center>
    <h4>
        Job definition is generated by <a href="${ job.generationPom }">Maven</a>
        using <a href="http://evgeny-goldin.com/wiki/Jenkins-maven-plugin">&quot;jenkins-maven-plugin&quot;</a> ${ timestamp ?: '' }.
        <br/>
        If you <a href="${ job.jenkinsUrl + '/job/' + job.id + '/configure' }">configure</a> this project manually -
        it will probably be <a href="${ job.generationPom }">overwritten</a>!
    </h4>
</center>
${ job.description }
<p/>
${ new DescriptionTableMarkup( job ).markup }
]]>
${ Markup.INDENT }""" ) // Indentation correction: closing </description> tag is not positioned correctly due to String content injected
        }
    }
}
