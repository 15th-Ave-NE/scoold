/*
 * Copyright 2013-2017 Erudika. https://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.scoold.controllers;

import com.erudika.para.client.ParaClient;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import static com.erudika.scoold.ScooldServer.HOMEPAGE;
import static com.erudika.scoold.ScooldServer.adminlink;
import com.erudika.scoold.core.Profile;
import com.erudika.scoold.utils.ScooldUtils;
import com.typesafe.config.ConfigValue;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	private final ScooldUtils utils;
	private final ParaClient pc;

	@Inject
	public AdminController(ScooldUtils utils) {
		this.utils = utils;
		this.pc = utils.getParaClient();
	}

	@GetMapping
    public String get(HttpServletRequest req, Model model) {
		if (!utils.isAuthenticated(req) || !utils.isAdmin(utils.getAuthUser(req))) {
			return "redirect:" + HOMEPAGE;
		}
		Map<String, Object> configMap = new HashMap<String, Object>();
		for (Map.Entry<String, ConfigValue> entry : Config.getConfig().entrySet()) {
			ConfigValue value = entry.getValue();
			configMap.put(Config.PARA + "_" + entry.getKey(), value != null ? value.unwrapped() : "-");
		}
		configMap.putAll(System.getenv());
		model.addAttribute("path", "admin.vm");
		model.addAttribute("title", utils.getLang(req).get("admin.title"));
		model.addAttribute("configMap", configMap);
		model.addAttribute("version", pc.getServerVersion());
        return "base";
    }

	@PostMapping
    public String forceDelete(@RequestParam Boolean confirmdelete, @RequestParam String id, HttpServletRequest req) {
		Profile authUser = utils.getAuthUser(req);
		if (confirmdelete && utils.isAdmin(authUser)) {
			ParaObject sobject = pc.read(id);
			if (sobject != null) {
				sobject.delete();
				logger.info("{} #{} deleted {} #{}", authUser.getName(), authUser.getId(),
						sobject.getClass().getName(), sobject.getId());
			}
		}
		return "redirect:" + adminlink;
	}
}
