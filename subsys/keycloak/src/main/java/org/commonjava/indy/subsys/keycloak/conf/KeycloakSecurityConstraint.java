/**
 * Copyright (C) 2011-2022 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.subsys.keycloak.conf;

import java.util.Arrays;
import java.util.List;

public class KeycloakSecurityConstraint {

	private String role;
	private String urlPattern;
	private List<String> methods;	
	
	public KeycloakSecurityConstraint(String role, String urlPattern,
			List<String> methods) {
		super();
		this.role = role;
		this.urlPattern = urlPattern;
		this.methods = methods;
	}
	
	public KeycloakSecurityConstraint(String role, String urlPattern,
			String[] methods) {
		super();
		this.role = role;
		this.urlPattern = urlPattern;
		this.methods = Arrays.asList(methods);
	}
	
	public KeycloakSecurityConstraint() {
		// keep default constructor
	}
	

	public String getRole() {
		return role;
	}
	public void setRole(String role) {
		this.role = role;
	}
	public String getUrlPattern() {
		return urlPattern;
	}
	public void setUrlPattern(String urlPattern) {
		this.urlPattern = urlPattern;
	}
	public List<String> getMethods() {
		return methods;
	}
	public void setMethods(List<String> methods) {
		this.methods = methods;
	}

	@Override
	public String toString() {
		return "SecurityConstraint [role=" + role + ", urlPattern="
				+ urlPattern + ", methods=" + methods + "]";
	}
	
	
}
