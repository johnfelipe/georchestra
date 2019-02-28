/*
 * Copyright (C) 2019 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.console.integration;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@EnableWebMvc
@ContextConfiguration(locations = { "classpath:/webmvc-config-test.xml" })
@PropertySource("classpath:console-it.properties")
@WebAppConfiguration
public class OrgsIT {

	public @Rule @Autowired IntegrationTestSupport support;

	@WithMockUser(username = "admin", roles = "SUPERUSER")
	public @Test void createAndGet() throws Exception {
		String orgName = ("IT_ORG_ " + RandomStringUtils.randomAlphabetic(8)).toLowerCase();

		create(orgName)
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.id").value(orgName.replace(" ", "_").toLowerCase()))
				.andExpect(jsonPath("$.shortName").value(orgName.toLowerCase()));

		support.perform(get("/private/orgs/" + orgName.replace(" ", "_")))
				.andExpect(jsonPath("$.id").value(orgName.replace(" ", "_").toLowerCase()))
				.andExpect(jsonPath("$.shortName").value(orgName.toLowerCase()))
				.andExpect(jsonPath("$.name").value("camptocamp"))
				.andExpect(jsonPath("$.cities").isEmpty())
				.andExpect(jsonPath("$.members").isEmpty())
				.andExpect(jsonPath("$.address").value("1, rue du pont"))
				.andExpect(jsonPath("$.orgType").value("Company"))
				.andExpect(jsonPath("$.pending").value(false));
	}

	@WithMockUser(username = "admin", roles = "SUPERUSER")
	public @Test void createAndGetWithEmptyDescription() throws Exception {
		String orgName = ("it_org_" + RandomStringUtils.randomAlphabetic(8)).toLowerCase();

		create(orgName);

		support.perform(get("/private/orgs/" + orgName))
			.andExpect(jsonPath("$.description").value(""));
	}

	@WithMockUser(username = "admin", roles = "SUPERUSER")
	public @Test void createAndGetWithDescription() throws Exception {
		String orgName = ("it_org_" + RandomStringUtils.randomAlphabetic(8)).toLowerCase();

		create(orgName, "/testData/createOrgWithMoreFieldsPayload.json");

		support.perform(get("/private/orgs/" + orgName))
				.andExpect(jsonPath("$.description").value("Nullam iaculis blandit justo, sed pellentesque justo ullamcorper eu.\nSed at justo quis leo fermentum suscipit.\nAliquam erat massa, euismod sed massa non, tempus faucibus est."));
	}



	private ResultActions create(String name) throws Exception {
		return create(name,"/testData/createOrgPayload.json");
	}

	private ResultActions create(String name, String payloadResourcePath) throws Exception {
		return support.perform(post("/private/orgs").content(support.readRessourceToString(payloadResourcePath).replace("{shortName}", name)));
	}

}
