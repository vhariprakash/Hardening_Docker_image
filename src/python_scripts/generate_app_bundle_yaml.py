import yaml
import re
import os
import shutil
import argparse
import json
import time
import glob
import subprocess

final_dict = {}


def read_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--git_user", help="Provide SSO of User")
    parser.add_argument("--git_token", help="Provide Git token of User")
    parser.add_argument("--artifactory_user", help="Provide SSO of User")
    parser.add_argument("--artifactory_token", help="Provide Artifactory token of User")
    parser.add_argument("--git_clone_path", default="app_bundles/", help="Provide path to clone repo")
    parser.add_argument("--git_clone_path_helm_hub", default="helm_hub/", help="Provide path to clone repo")
    parser.add_argument("--git_charts_path", default="ehl-helm-hub/component_tested_charts/Chart.yaml",
                        help="Provide path to Chart.yaml")
    parser.add_argument("--git_pacakge_path", default="ehl-helm-hub/Packaging_Lookup.json",
                        help="Provide path to package")
    parser.add_argument("--jsonforapp", default="complete", help="Provide name of package")
    parser.add_argument("--helm_hub_repo_name", default="Edison-Imaging-Service/ehl-helm-hub", help="Provide Full Repo name")
    parser.add_argument("--repo_name", default="Edison-Imaging-Service/ehs-app-bundle-registration", help="Provide Full Repo name")
    parser.add_argument("--ees_deploy_name", default="Edison-Imaging-Service/EES-Deploy", help="Provide Full Repo name")
    parser.add_argument("--ees_deploy_clone_path", default="ees_deploy/", help="Provide path to clone repo")
    parser.add_argument("--catalog_path", default="EES-Deploy/ISO-Catalogue.yaml", help="Provide path to catalog yaml")
    parser.add_argument("--build_id", help="provide Build id", default="1")
    parser.add_argument("--app_bundle_branch_name", help="branch name", default="catalyst_dev")
    parser.add_argument("--app_json_filename", help="branch name", default="universal_app_installer.json")
    args = parser.parse_args()
    return args


def format_git_url(sso, token, repo_name):
    git_url = "https://" + sso + ":" + token + "@gitlab-gxp.cloud.health.ge.com/" + repo_name + ".git"
    return git_url


def clone_repo_single_branch(git_clone_branch_path, repo_path, branch_name):
    """
    Clone given Repository to specified path
    """
    if os.path.exists(git_clone_branch_path) and os.path.isdir(git_clone_branch_path):
        shutil.rmtree(git_clone_branch_path)
    if not os.path.exists(git_clone_branch_path):
        os.makedirs(git_clone_branch_path)
    cmd = "cd " + git_clone_branch_path + "; git clone " + repo_path
    cmd = "cd " + git_clone_branch_path + "; git clone --single-branch " + '--branch ' + branch_name + ' ' + repo_path
    process = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    print(" --- Runinng Subprocess Command --- ")
    print(process.stdout)
    if process.returncode == 0:
        return process.returncode
    else:
        print("******************************************************************************************")
        print("******************************** ERROR MESSAGE START *************************************")
        print("Failed to clone: ", repo_path.split('/')[-1][:-4])
        print(process.stderr)
        print("******************************** ERROR MESSAGE END ***************************************")
        print("******************************************************************************************")


def read_yaml(yaml_path):
    with open(yaml_path, 'r') as stream:
        try:
            yaml_content = yaml.safe_load(stream)
        except yaml.YAMLError as exc:
            print(exc)
    return yaml_content


def write_yaml_file(yaml_content, yaml_path):
    with open(yaml_path, 'w') as outfile:
        yaml.dump(yaml_content, outfile, default_flow_style=False)


if __name__ == "__main__":
    args = read_arguments()
    git_url = format_git_url(args.git_user, args.git_token, args.repo_name)
    clone_repo_single_branch(args.git_clone_path, git_url, args.app_bundle_branch_name)
    git_url = format_git_url(args.git_user, args.git_token, args.helm_hub_repo_name)
    clone_repo_single_branch(args.git_clone_path_helm_hub, git_url, 'master')
    # clone_helm_hub_repo(args.git_clone_path, git_url, args.branch_name)
    chart_yaml_path = args.git_clone_path_helm_hub + args.git_charts_path
    chart_yaml_content = read_yaml(chart_yaml_path)
    for filename in glob.glob(args.git_clone_path + 'ehs-app-bundle-registration/templates/*.yaml'):
        app_bundle_content = read_yaml(filename)
        for app in app_bundle_content['spec']['apps']:
            for chart in chart_yaml_content['dependencies']:
                if app['name'] == chart['name']:
                    print("*************************************************************")
                    print("Service Name: ", app['name'])
                    print("Found Version: ", app['version'])
                    print("Replacing with: ", chart['version'])
                    app['version'] = chart['version']
                    print("*************************************************************")
        print(" -------- App Bundle After Replacing Version -------- ")
        print(app_bundle_content)
        write_yaml_file(app_bundle_content, filename)
    cmd = "cd "+args.git_clone_path+"ehs-app-bundle-registration;"+" helm package ."
    process = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    print(" --- Runinng helm package --- ")
    print(cmd)
    print(process.stdout)
    cmd = "cd "+args.git_clone_path+"ehs-app-bundle-registration;"+" curl -u 212689636:AKCp5fUsH2tH2vmaCjTVcYSQqtrHZpRF18YVn5mrTixd2N4jK12eE1dsAityv9tPBEoQbVynE -X PUT 'https://blr-artifactory.cloud.health.ge.com/artifactory/helm-ees-all/app-bundle-registration/app-bundle-registration-0.0.1.tgz' -T app-bundle-registration-0.0.1.tgz"
    process = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    print(" --- Pushing to Artifactory --- ")
    print(cmd)
    print(process.stdout)
