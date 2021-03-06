/**
 * Copyright 2015 Jesse Jaara <jesse.jaara@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.Huulivoide.JIconManager;

/**
 * ThemeNotFoundException as the name says tells if the requested theme is not
 * found in the system.
 */
public class ThemeNotFoundException extends Exception
{
    //    region Public    //
    /////////////////////////

    public ThemeNotFoundException(String message)
    {
        super(message);
    }

    /////////////////////////
    //  endregion Public   //
}
