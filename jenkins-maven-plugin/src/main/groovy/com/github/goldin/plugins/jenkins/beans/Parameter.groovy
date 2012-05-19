package com.github.goldin.plugins.jenkins.beans

import groovy.xml.MarkupBuilder
import org.gcontracts.annotations.Requires


enum  ParameterType { bool, choice, string, password, run, file, jira }
class Parameter
{
    String        name        = 'UNDEFINED'
    ParameterType type        = null
    String        value       = ''
    String        description = ''

    void setType( String type ) { this.type = ParameterType.valueOf( ParameterType, type ) }

    @Override
    int     hashCode ()           { "[$name][$type]".hashCode()  }

    @Override
    boolean equals   ( Object o ) { ( o instanceof Parameter ) &&
                                    ( this.name            == o.name            ) &&
                                    ( this.type.toString() == o.type.toString() ) }


    @Requires({ builder })
    void addMarkup ( MarkupBuilder builder )
    {
        final nameDescription = { builder.name( name ); builder.description( description )}

        builder.with {
            switch ( type )
            {
                case ParameterType.bool:
                    'hudson.model.BooleanParameterDefinition' {
                        nameDescription()
                        defaultValue( Boolean.valueOf( value ))
                    }
                    break

                case ParameterType.choice:
                    'hudson.model.ChoiceParameterDefinition' {
                        nameDescription()
                        choices( class: 'java.util.Arrays$ArrayList' ) {
                            a( class: 'string-array' ) {
                                value.split( /\s*,\s*/ ).each { string( it ) }
                            }
                        }
                    }
                    break

                case ParameterType.string:
                    'hudson.model.StringParameterDefinition' {
                        nameDescription()
                        defaultValue( value )
                    }
                    break

                case ParameterType.password:
                    'hudson.model.PasswordParameterDefinition' {
                        nameDescription()
                        defaultValue( value )
                    }
                    break

                case ParameterType.run:
                    'hudson.model.RunParameterDefinition' {
                        nameDescription()
                        projectName( value )
                    }
                    break

                case ParameterType.file:
                    'hudson.model.FileParameterDefinition' {
                        nameDescription()
                    }
                    break

                case ParameterType.jira:
                    'hudson.plugins.jira.JiraProjectProperty' {
                        siteName( value )
                    }
                    break

                default:
                    throw new RuntimeException( "Unsupported parameter type [$type]" )
            }
        }
    }
}

