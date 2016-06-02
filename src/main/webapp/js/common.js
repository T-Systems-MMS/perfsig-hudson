/*
 * Copyright (c) 2014 T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

require.config({
    baseUrl: resURL + '/plugin/performance-signature/js/',
    paths: {
        'jquery': "jquery-2.2.4.min",
        'bootstrap': "bootstrap.min",
        'datatables.net': "jquery.dataTables.min",
        'datatables.bootstrap': "dataTables.bootstrap.min",
        'gridster': "jquery.gridster.min",
        'lightbox': "lightbox.min"
    },
    shim: {
        'bootstrap': ['jquery'],
        'datatables.bootstrap': ['datatables.net'],
        'gridster': ['jquery']
    },
    map: {
        '*': {
            'jquery': 'jQueryNoConflict'
        },
        'jQueryNoConflict': {
            'jquery': 'jquery'
        }
    }
});
define('jQueryNoConflict', ['jquery'], function ($) {
    return $.noConflict();
});
if (Prototype.BrowserFeatures.ElementExtensions) {
    require(['jquery', 'bootstrap'], function ($) {
        // Fix incompatibilities between BootStrap and Prototype
        var disablePrototypeJS = function (method, pluginsToDisable) {
                var handler = function (event) {
                    event.target[method] = undefined;
                    setTimeout(function () {
                        delete event.target[method];
                    }, 0);
                };
                pluginsToDisable.each(function (plugin) {
                    $(window).on(method + '.bs.' + plugin, handler);
                });
            },
            pluginsToDisable = ['collapse', 'dropdown', 'modal', 'tooltip', 'popover', 'tab'];
        disablePrototypeJS('show', pluginsToDisable);
        disablePrototypeJS('hide', pluginsToDisable);
    });
}
