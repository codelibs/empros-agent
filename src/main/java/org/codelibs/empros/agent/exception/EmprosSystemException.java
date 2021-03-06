/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.empros.agent.exception;

import org.codelibs.core.exception.ClRuntimeException;

/**
 * EmprosSystemException is a system exception class on Empros Agent.
 *
 * @author shinsuke
 */
public class EmprosSystemException extends ClRuntimeException {

    private static final long serialVersionUID = 1L;

    public EmprosSystemException(final String messageCode, final Object[] args,
                                 final Throwable cause) {
        super(messageCode, args, cause);
    }

    public EmprosSystemException(final String messageCode, final Object[] args) {
        super(messageCode, args);
    }

    public EmprosSystemException(final String messageCode, final Throwable cause) {
        super(messageCode, new Object[]{}, cause);
    }

    public EmprosSystemException(final String messageCode) {
        super(messageCode, new Object[0]);
    }

}
