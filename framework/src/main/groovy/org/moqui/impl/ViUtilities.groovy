/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.impl

import dtq.rockycube.query.SqlExecutor
import org.moqui.context.ElasticFacade
import org.moqui.context.ExecutionContext
import org.moqui.impl.entity.EntityJavaUtil

import java.sql.Connection
import org.slf4j.Logger

import java.text.SimpleDateFormat
import java.util.regex.Pattern

/** These are utilities that should exist elsewhere, but I can't find a good simple library for them, and they are
 * stupid but necessary for certain things.
 */
class ViUtilities {
    static final String removeNonumericCharacters(String inputValue) {
        return inputValue.replaceAll("[^\\d]", "")
    }

    static Long stringToUnix(String input) {
        if (input.isNumber()) return Long.parseLong(input)

        // is it date format?
        def date_1_rec = Pattern.compile("\\d{4}-\\d{2}-\\d{2}:\\d{2}:\\d{2}:\\d{2}")
        if (input==~date_1_rec)
        {
            def date_1 = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss").parse(input)
            return date_1.getTime() / 1000
        }
        return null
    }

    public static boolean isAlphaNumeric(String s){
        String pattern= '^[a-zA-Z0-9]*$'
        return s.matches(pattern);
    }

    public static boolean isAlphabetic(String s){
        String pattern= '^[a-zA-Z]*$'
        return s.matches(pattern);
    }

    public static String formattedString(String conversionType, String input)
    {
        // EntityJavaUtil - for the sake of converting strings
        EntityJavaUtil util = new EntityJavaUtil()

        switch (conversionType)
        {
            case 'underscoredToCamelCase-firstUpper':
                return util.underscoredToCamelCase(input, true)
                break
            case 'underscoredToCamelCase':
                return util.underscoredToCamelCase(input, false)
                break
            default:
                return null
        }
    }

    static final String removeCommas(String inputValue) {
        return inputValue.replaceAll(",", ".")
    }

    /*QUERY HELPERS*/
    static String calcPagedQuery(String query, Integer pageIndex, Integer pageSize)
    {
        return SqlExecutor.calcPagedQuery(query, pageIndex, pageSize)
    }

    static String calcTotalQuery(String query)
    {
        return SqlExecutor.calcTotalQuery(query)
    }

    static HashMap<String, Integer> paginationInfo(clLoadTotal, String query, Integer pageIndex, Integer pageSize)
    {
        return SqlExecutor.paginationInfo(clLoadTotal, query, pageIndex, pageSize)
    }

    static HashMap<String, Object> executeQuery(
            Connection conn,
            Logger logger,
            String query,
            Integer pageIndex = 1,
            Integer pageSize = 20)
    {
        return SqlExecutor.executeQuery(conn, logger,query, pageIndex, pageSize)
    }
}